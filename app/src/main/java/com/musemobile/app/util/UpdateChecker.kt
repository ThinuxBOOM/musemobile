package com.musemobile.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.musemobile.app.BuildConfig

class UpdateChecker(private val context: Context) {

    companion object {
        private const val OWNER = "ThinuxBOOM"
        private const val REPO = "MuseMobile"
        private const val PREFS_NAME = "musemobile_prefs"
        private const val KEY_LAST_CHECK = "LastUpdateCheck"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }

    fun autoCheck(onUpdateAvailable: (String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return
        if (isMeteredOrRoaming()) return

        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        GitHubApi.fetchLatestRelease(OWNER, REPO) { release ->
            val latest = release?.tagName?.removePrefix("v") ?: return@fetchLatestRelease
            val current = BuildConfig.VERSION_NAME

            if (isNewer(latest, current)) {
                val url = release.htmlUrl.ifBlank {
                    "https://github.com/$OWNER/$REPO/releases/latest"
                }
                onUpdateAvailable(url)
            }
        }
    }

    /** Auto-checks must never burn metered data or roam: fail silent, retry next launch. */
    private fun isMeteredOrRoaming(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".")
        val currentParts = current.split(".")
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
