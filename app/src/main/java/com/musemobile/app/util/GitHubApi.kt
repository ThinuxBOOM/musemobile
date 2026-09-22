package com.musemobile.app.util

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String
)

object GitHubApi {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "GitHubApi-Worker").apply { isDaemon = true }
        }

    fun fetchLatestRelease(
        owner: String,
        repo: String,
        onResult: (GitHubRelease?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val release = if (conn.responseCode == 200) {
                    // use{} closes the stream on the error path too (old code
                    // leaked the connection when readText() threw).
                    val text = conn.inputStream.use { it.bufferedReader().readText() }
                    val json = JSONObject(text)
                    GitHubRelease(
                        tagName = json.optString("tag_name", ""),
                        name = json.optString("name", ""),
                        body = json.optString("body", ""),
                        publishedAt = json.optString("published_at", ""),
                        htmlUrl = json.optString("html_url", "")
                    )
                } else {
                    null
                }

                Handler(Looper.getMainLooper()).post { onResult(release) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { onResult(null) }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }
}