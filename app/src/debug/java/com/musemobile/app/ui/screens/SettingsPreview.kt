package com.musemobile.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.musemobile.app.ui.theme.SpotifyTheme

/** Dev-only preview (debug source set so release never links ui.tooling). */
@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("preview_prefs", Context.MODE_PRIVATE) }
    SpotifyTheme {
        SettingsContent(
            modifier = Modifier.fillMaxSize(),
            prefs = prefs,
            materialYou = false,
            onMaterialYouChange = {},
            amoledThemeState = false,
            onAmoledThemeChange = {},
            hideTopBar = false,
            onHideTopBarChange = {},
            landscapeMode = false,
            onLandscapeModeChange = {},
            keepScreenOn = false,
            onKeepScreenOnChange = {},
            paletteSeed = null,
            onPaletteSeedChange = {},
            onConnectionModeChange = {},
            onOfflineModeChange = {},
            onSaveProfile = { _, _ -> },
            onLoadProfile = {},
            onDeleteProfile = {},
            onClearCache = {},
            onClearData = {},
            blockServiceWorker = true,
            onBlockServiceWorkerChange = {}
        )
    }
}
