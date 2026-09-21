package com.musemobile.app.webview.helpers

import org.json.JSONObject

fun buildCustomCssJs(css: String): String {
    val jsonCss = JSONObject.quote(css)
    return """
        (function(){
            var cst = document.getElementById('musemobile-custom-css');
            if ($jsonCss === "") {
                if (cst) cst.remove();
                return;
            }
            if (!cst) {
                cst = document.createElement('style');
                cst.id = 'musemobile-custom-css';
            }
            cst.textContent = $jsonCss;
            var target = document.head || document.documentElement;
            if (target && !cst.parentNode) {
                target.appendChild(cst);
            }
        })();
    """.trimIndent()
}
