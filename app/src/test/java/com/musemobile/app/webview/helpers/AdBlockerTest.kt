package com.musemobile.app.webview.helpers

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Functional tests for the adblock matching core (pure JVM logic, no WebView).
 *
 * These lock in the behaviors the native + JS layers depend on:
 * - analytics endpoints are stubbed, music/player traffic is not
 * - ad audio hosts are probed, music CDNs are never silent-swapped
 * - harvested ad content IDs match exactly and stay bounded
 */
class AdBlockerTest {

    @Before
    @After
    fun resetStore() {
        AdIdStore.clear()
    }

    // --- Layer B1: analytics stubbing ------------------------------------

    @Test
    fun analyticsDomains_areBlocked() {
        assertTrue(isAnalyticsDomain("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(isAnalyticsDomain("https://pagead2.googlesyndication.com/getconfig"))
        assertTrue(isAnalyticsDomain("https://sentry.io/api/123/envelope/"))
        assertTrue(isAnalyticsDomain("https://aet.spotify.com/v1/events"))
        assertTrue(isAnalyticsDomain("https://open.spotify.com/gabo-receiver-service/public/v3/events"))
        assertTrue(isAnalyticsDomain("https://tracker.samplicio.us/track"))
    }

    @Test
    fun playerAndMusicTraffic_areNotAnalytics() {
        assertFalse(isAnalyticsDomain("https://open.spotify.com/"))
        assertFalse(isAnalyticsDomain("https://gew4-spclient.spotify.com/audio/abc123"))
        assertFalse(isAnalyticsDomain("https://audio-fa.scdn.co/audio/abc123"))
    }

    @Test
    fun workboxChunk_isNotBlocked_regressionChunkLoadError() {
        // Blocking Spotify's lazy workbox chunk crashed init with
        // ChunkLoadError -> "Something wrong with page". Must stay allowed.
        assertFalse(isAnalyticsDomain("https://open.spotify.com/chunk.6201.workbox-window.js"))
    }

    // --- Layer B2: ad-audio host matching (non-proxy probe gate) ---------

    @Test
    fun adAudioHosts_areDetected() {
        assertTrue(isAdAudioUrl("https://audio-akp-quiet.akamaized.net/audio/ad123"))
        assertTrue(isAdAudioUrl("https://audio-ak.scdn.co/mp3-ad/ad456"))
        assertTrue(isAdAudioUrl("https://amillionads.com/ad789"))
        assertTrue(isAdAudioUrl("https://adstudio-assets.scdn.co/creatives/x"))
        assertTrue(isAdAudioUrl("https://audio-fa.spotifycdn.com/audio/ad000"))
    }

    // --- Layer B3: static ad-CDN matching (proxy mode) --------------------

    @Test
    fun adCdnPatterns_areDetected() {
        assertNotNull(matchAdCdn("https://mp3ad.scdn.co/ad123.mp3"))
        assertNotNull(matchAdCdn("https://open.spotify.com/vast/vast.xml"))
        assertNotNull(matchAdCdn("https://ads.spotify.com/ad-logic/ping"))
        assertNotNull(matchAdCdn("https://pixel.spotify.com/pixel"))
        assertNotNull(matchAdCdn("https://audio-ads.spotify.com/ad"))
        assertNotNull(matchAdCdn("https://2mdn.net/ad"))
    }

    @Test
    fun musicCdn_isNeverAdCdn() {
        // Silencing these would mute real music. Locked in per the
        // "never music CDN" rule in AdBlocker.kt.
        assertNull(matchAdCdn("https://gew4-spclient.spotify.com/audio/music123"))
        assertNull(matchAdCdn("https://audio-fa.scdn.co/audio/music456"))
        assertNull(matchAdCdn("https://podz-content.spotifycdn.com/audio/ep789"))
        assertNull(matchAdCdn("https://audio-fa.spotifycdn.com/audio/music000"))
        assertNull(matchAdCdn("https://i.scdn.co/image/coverart"))
        assertNull(matchAdCdn("https://open.spotify.com/track/abc"))
    }

    // --- Layer B4: harvested ad content IDs -------------------------------

    @Test
    fun harvestedIds_matchExactMediaRequests() {
        assertTrue(AdIdStore.addAll(listOf("abc12345", "zzz_--12")))
        assertTrue(AdIdStore.matches("https://audio-fa.scdn.co/audio/abc12345?x=1"))
        assertTrue(AdIdStore.matches("https://gew4-spclient.spotify.com/audio/zzz_--12"))
        assertFalse(AdIdStore.matches("https://audio-fa.scdn.co/audio/music999"))
    }

    @Test
    fun invalidIds_areRejected() {
        assertFalse(AdIdStore.addAll(listOf("short", "has space12", "bad!chars1", "")))
        assertFalse(AdIdStore.matches("https://audio-fa.scdn.co/audio/short"))
    }

    @Test
    fun clear_resetsMatching() {
        AdIdStore.addAll(listOf("abc12345"))
        assertTrue(AdIdStore.matches("https://x/abc12345"))
        AdIdStore.clear()
        assertFalse(AdIdStore.matches("https://x/abc12345"))
    }

    @Test
    fun store_isBounded_leastRecentlySeenEvicted() {
        val ids = (0 until 40).map { "fileid_%02d".format(it) }
        AdIdStore.addAll(ids)
        // First 8 evicted, last 32 kept.
        assertFalse(AdIdStore.matches("https://x/fileid_00"))
        assertFalse(AdIdStore.matches("https://x/fileid_07"))
        assertTrue(AdIdStore.matches("https://x/fileid_08"))
        assertTrue(AdIdStore.matches("https://x/fileid_39"))
    }

    // --- Power-hog detection (video/canvas parking) -----------------------

    @Test
    fun powerHogUrls_areDetected_musicAudioIsNot() {
        assertTrue(isPowerHogUrl("https://canvasset.scdn.co/canvas/x"))
        assertTrue(isPowerHogUrl("https://open.spotify.com/video.mp4"))
        assertFalse(isPowerHogUrl("https://audio-fa.scdn.co/audio/music123"))
    }
}
