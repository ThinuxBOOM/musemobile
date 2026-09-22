package com.musemobile.app.net

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Process-wide OkHttp sharing for battery + memory.
 *
 * Before: YTPlayerUtils, PlayerJsFetcher, PoTokenWebView and NewPipeDownloaderImpl
 * each built an isolated OkHttpClient with its own pool/dispatcher, so idle
 * connections could never be reused across them and each scaled its own
 * thread pool. Now they share one [pool] and one [dispatcher] while keeping
 * their own proxy/timeout configuration.
 */
object SharedOkHttp {
    /** Small pool: music streaming rarely needs more than a few concurrent conns. */
    val pool: ConnectionPool =
        ConnectionPool(5, 5, TimeUnit.MINUTES)

    /** Bounded dispatcher so background fetches can't spawn unbounded threads. */
    val dispatcher: Dispatcher =
        Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        }

    /** Baseline config every app client should share (timeouts fail fast on mobile). */
    fun builder(proxy: Proxy? = null): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectionPool(pool)
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .proxy(proxy)
}
