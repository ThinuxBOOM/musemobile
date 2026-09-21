package com.musemobile.app.util

import android.content.Context

/**
 * One-time migration from legacy "spotilol" storage names to "musemobile".
 * Runs idempotently on startup; all failures are swallowed so it can never crash the app.
 */
object PrefsMigration {
    private const val NEW_PREFS = "musemobile_prefs"
    private const val OLD_PREFS = "spotilol_prefs"

    fun migrate(context: Context) {
        runCatching { migratePlainPrefs(context) }
        runCatching { migrateSecurePassword(context) }
        runCatching { migrateProfiles(context) }
    }

    private fun migratePlainPrefs(context: Context) {
        val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
        val new = context.getSharedPreferences(NEW_PREFS, Context.MODE_PRIVATE)
        val oldAll = old.all
        if (oldAll.isEmpty()) return
        val editor = new.edit()
        var changed = false
        for ((k, v) in oldAll) {
            if (!new.contains(k)) {
                when (v) {
                    is Boolean -> editor.putBoolean(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is String -> editor.putString(k, v)
                    is Set<*> -> editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                    null -> {}
                }
                changed = true
            }
        }
        // Normalize legacy PlayerMode value stored by old versions.
        if (new.getString("PlayerMode", null) == "spotilol") {
            editor.putString("PlayerMode", "musemobile")
            changed = true
        } else if (old.getString("PlayerMode", null) == "spotilol" && !new.contains("PlayerMode")) {
            editor.putString("PlayerMode", "musemobile")
            changed = true
        }
        if (changed) editor.apply()
    }

    private fun migrateSecurePassword(context: Context) {
        val key = "keystore_password"
        val newName = "musemobile_secure_prefs"
        val oldName = "spotilol_secure_prefs"
        runCatching {
            val newPrefs = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
            if (newPrefs.contains(key)) return
            val oldPrefs = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
            val pw = oldPrefs.getString(key, null)
            if (!pw.isNullOrEmpty()) newPrefs.edit().putString(key, pw).apply()
        }
        // EncryptedSharedPreferences variants are handled by LocalProxyManager/ProfileManager
        // fallbacks; plain copy above covers the common non-encrypted fallback path.
    }

    private fun migrateProfiles(context: Context) {
        val newName = "musemobile_profiles"
        val oldName = "spotilol_profiles"
        runCatching {
            val newPrefs = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
            if (newPrefs.contains("profiles")) return
            val oldPrefs = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
            val raw = oldPrefs.getString("profiles", null)
            if (!raw.isNullOrEmpty()) newPrefs.edit().putString("profiles", raw).apply()
        }
    }

    /** Treats the legacy stored value as the new brand value. */
    fun normalizePlayerMode(raw: String?): String =
        if (raw == null || raw == "spotilol") "musemobile" else raw
}
