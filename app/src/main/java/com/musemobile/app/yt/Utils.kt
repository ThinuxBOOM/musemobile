package com.musemobile.app.yt

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Forwards to Crashlytics (no-op until collection is enabled after first frame). */
fun reportException(throwable: Throwable) {
    Log.e("Spl-DL", "Exception", throwable)
    runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
}
