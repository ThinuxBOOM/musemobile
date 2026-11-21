package com.musemobile.app.webview.injections

/**
 * Page-level ad-state hook, ported from Blockify's page-hook.js.
 *
 * Injected from SpotifyWebViewClient.onPageStarted(), right after
 * FetchOverride, so the wrappers compose: this hook sits OUTSIDE the
 * FetchOverride wrapper and observes whatever comes back through it - in
 * proxy mode too (mngFetch's response shim exposes .json()/.clone(), so
 * cloning still works).
 *
 * What it does:
 *  1. Wraps window.fetch - player-state and player-message responses are
 *     cloned and parsed without consuming the response Spotify receives.
 *  2. Wraps window.WebSocket - dealer messages are inspected in both
 *     connection modes, including nested state and ad metadata.
 *  3. Performs a bounded recursive walk instead of assuming one fixed
 *     state_machine shape. Ad-marked objects yield audio file IDs from
 *     manifests (file_ids_mp3 / file_ids_external / file_ids / alternatives).
 *  4. Publishes the bounded ID list to the native layer via
 *     AndBridge.recAdContentIds() whenever it changes (max 32, deduped).
 *
 * The native side (AdIdStore + shouldInterceptRequest) then redirects any
 * media request whose URL contains one of those IDs to silent.mp3.
 *
 * No DOM dependency (unlike Blockify's DOM-attribute bridge): Android's
 * WebView main-world injection can call the JS bridge directly.
 */

object AdStateHook {
    const val CONTENT = """
        (function(){
            if (window.__splAdStateHook) return;
            window.__splAdStateHook = 1;
            var MAX_IDS = 32;
            var MAX_JSON = 8000000;
            var knownIds = {};
            var idOrder = [];
            var lastPub = '';
            var dirty = false;
            var textDecoder = null;
            var ID_RE = /^[a-zA-Z0-9_-]{8,128}$/;

            function safeId(v){
                return typeof v === 'string' && ID_RE.test(v);
            }
            function remember(v){
                if (!safeId(v) || knownIds[v]) return;
                knownIds[v] = 1;
                idOrder.push(v);
                dirty = true;
                while (idOrder.length > MAX_IDS) {
                    delete knownIds[idOrder.shift()];
                }
            }
            function publish(){
                if (!dirty) return;
                var ser;
                try { ser = JSON.stringify(idOrder); } catch(e){ return; }
                dirty = false;
                if (ser === lastPub) return;
                lastPub = ser;
                try { AndBridge.recAdContentIds(ser); } catch(e){}
                try { AndBridge.dbg('i', 'adIds=' + idOrder.length); } catch(e){}
            }
            function isAdTrack(t){
                if (!t || typeof t !== 'object') return false;
                var m = t.metadata || t.meta || {};
                var uri = t.uri || t.canonical_uri || t.content_uri ||
                    m.uri || m.canonical_uri || m.content_uri || '';
                if (typeof uri === 'string' &&
                    (uri.indexOf('spotify:ad:') === 0 || uri.indexOf('spotify:advertisement') === 0)) return true;

                var name = t.name || t.title || m.title || m.name || '';
                if (typeof name === 'string' && name.trim().toLowerCase() === 'advertisement') return true;

                var candidates = [
                    t.content_type, t.contentType, t.type, t.is_ad, t.isAd,
                    t.is_advertisement, t.advertisement,
                    m.content_type, m.contentType, m.type, m.is_ad, m.isAd,
                    m.is_advertisement, m.advertisement
                ];
                for (var i = 0; i < candidates.length; i++) {
                    var v = candidates[i];
                    if (v === true) return true;
                    if (typeof v === 'string') {
                        var normalized = v.trim().toUpperCase();
                        if (normalized === 'AD' || normalized === 'ADVERTISEMENT' ||
                            normalized === 'TRUE' || normalized === 'SPOTIFY:AD') return true;
                    }
                }
                return false;
            }
            function harvestManifest(man){
                if (typeof man === 'string') {
                    try { man = JSON.parse(man); } catch(e){ return; }
                }
                if (!man || typeof man !== 'object') return;
                var groups = [man.file_ids_mp3, man.file_ids_external, man.file_ids, man.alternatives];
                for (var g = 0; g < groups.length; g++) {
                    var group = groups[g];
                    if (!group || !group.length) continue;
                    for (var i = 0; i < group.length; i++) {
                        var entry = group[i];
                        if (typeof entry === 'string') remember(entry);
                        else if (entry) {
                            remember(entry.file_id);
                            remember(entry.fileId);
                            remember(entry.id);
                        }
                    }
                }
            }
            function inspectAdObject(t){
                if (!isAdTrack(t)) return;
                remember(t.file_id);
                remember(t.fileId);
                remember(t.audio_file_id);
                remember(t.audioFileId);
                harvestManifest(t.manifest);
                harvestManifest(t.audio_manifest);
                harvestManifest(t.audioManifest);
                harvestManifest(t.media_manifest);
                harvestManifest(t.mediaManifest);
                var metadata = t.metadata || t.meta;
                if (metadata && typeof metadata === 'object') {
                    harvestManifest(metadata.manifest);
                    harvestManifest(metadata.audio_manifest);
                    harvestManifest(metadata.audioManifest);
                }
            }
            function inspectPayload(root){
                if (!root || typeof root !== 'object') return;
                // Spotify has moved ad tracks between state_machine, queue,
                // payloads and nested metadata over time. Walk JSON safely,
                // but cap both depth and total nodes to protect the UI thread.
                var stack = [{ value: root, depth: 0 }];
                var visited = 0;
                while (stack.length && visited < 12000) {
                    var current = stack.pop();
                    var value = current.value;
                    if (!value || typeof value !== 'object' || current.depth > 16) continue;
                    visited++;
                    if (value instanceof Array) {
                        for (var i = 0; i < value.length && visited + i < 12000; i++) {
                            if (value[i] && typeof value[i] === 'object') {
                                stack.push({ value: value[i], depth: current.depth + 1 });
                            }
                        }
                        continue;
                    }
                    inspectAdObject(value);
                    var keys;
                    try { keys = Object.keys(value); } catch(e){ continue; }
                    for (var k = 0; k < keys.length && visited + k < 12000; k++) {
                        var child;
                        try { child = value[keys[k]]; } catch(e){ continue; }
                        if (child && typeof child === 'object') {
                            stack.push({ value: child, depth: current.depth + 1 });
                        }
                    }
                }
                publish();
            }
            function parseJson(v){
                if (typeof v !== 'string' || v.length > MAX_JSON) return null;
                try { return JSON.parse(v); } catch(e){ return null; }
            }
            function inspectSocketData(v){
                if (typeof v === 'string') {
                    // Most dealer messages are state_machine payloads, but ad
                    // metadata can also arrive as a separate nested event.
                    // Keep a cheap keyword gate while accepting both shapes.
                    if (v.indexOf('state_machine') === -1 &&
                        v.indexOf('stateMachine') === -1 &&
                        v.indexOf('spotify:ad:') === -1 &&
                        v.indexOf('ADVERTISEMENT') === -1 &&
                        v.indexOf('"is_ad"') === -1 &&
                        v.indexOf('"content_type"') === -1) return;
                    var p = parseJson(v);
                    if (p) inspectPayload(p);
                    return;
                }
                if (typeof Blob === 'function' && v instanceof Blob) {
                    if (v.size <= MAX_JSON) v.text().then(inspectSocketData).catch(function(){});
                    return;
                }
                if (typeof ArrayBuffer === 'function' && v instanceof ArrayBuffer) {
                    if (v.byteLength <= MAX_JSON) {
                        try {
                            if (!textDecoder) textDecoder = new TextDecoder();
                            inspectSocketData(textDecoder.decode(v));
                        } catch(e){}
                    }
                }
            }

            // fetch wrapper - sits OUTSIDE FetchOverride's wrapper, so state
            // responses are visible in normal AND proxy mode (mngFetch shim
            // exposes .json()). The original promise is returned untouched.
            var pageFetch = window.fetch;
            if (typeof pageFetch === 'function') {
                window.fetch = function(input, init){
                    var p = pageFetch.apply(this, arguments);
                    try {
                        var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                        if (url.indexOf('/state') !== -1 ||
                            url.indexOf('connect-state') !== -1 ||
                            url.indexOf('melody/v1/msg') !== -1 ||
                            url.indexOf('/track-playback/') !== -1) {
                            Promise.resolve(p).then(function(res){
                                try {
                                    var c = res.clone();
                                    c.json().then(inspectPayload).catch(function(){});
                                } catch(e){}
                            }).catch(function(){});
                        }
                    } catch(e){}
                    return p;
                };
            }

            // WebSocket wrapper - dealer deltas for every track change arrive
            // here regardless of connection mode. Plain constructor function
            // instead of Proxy for maximum WebView compatibility.
            var NativeWS = window.WebSocket;
            if (typeof NativeWS === 'function' && !NativeWS.__splWrapped) {
                var WrappedWS = function(url, protocols){
                    var sock = protocols === undefined ? new NativeWS(url) : new NativeWS(url, protocols);
                    sock.addEventListener('message', function(ev){
                        try { inspectSocketData(ev.data); } catch(e){}
                    });
                    return sock;
                };
                WrappedWS.prototype = NativeWS.prototype;
                WrappedWS.CONNECTING = NativeWS.CONNECTING;
                WrappedWS.OPEN = NativeWS.OPEN;
                WrappedWS.CLOSING = NativeWS.CLOSING;
                WrappedWS.CLOSED = NativeWS.CLOSED;
                WrappedWS.__splWrapped = true;
                window.WebSocket = WrappedWS;
            }
        })();
    """
}