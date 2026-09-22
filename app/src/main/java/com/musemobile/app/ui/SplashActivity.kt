package com.musemobile.app.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musemobile.app.BuildConfig
import com.musemobile.app.proxy.LocalProxyManager
import com.musemobile.app.ui.theme.SpotifyTheme
import com.musemobile.app.util.PrefsMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MonochromeAccent = Color(0xFFE0E0E0)

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // One-time upgrade: copy legacy spotilol prefs/profiles into musemobile names.
        // Steady-state cost is a single cached boolean (see PrefsMigration).
        PrefsMigration.migrate(this)

        requestedOrientation = if (
            getSharedPreferences("musemobile_prefs", MODE_PRIVATE)
                .getBoolean("LandscapeMode", false)
        ) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // NOTE: Firebase collection stays disabled here (see manifest flags) and
        // is enabled after MainActivity's first frame; APP_OPEN is logged there.

        setContent {
            var certInstalled by remember { mutableStateOf(false) }
            var checkDone by remember { mutableStateOf(false) }
            var checking by remember { mutableStateOf(true) }
            var exiting by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                if (getSharedPreferences("musemobile_prefs", MODE_PRIVATE)
                        .getBoolean("OfflineMode", false)
                ) {
                    startActivity(Intent(this@SplashActivity, OfflineActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    return@LaunchedEffect
                }
                // Compute off-main, assign on-main (snapshot state is main-confined).
                val installed = withContext(Dispatchers.IO) {
                    val useProxy = getSharedPreferences("musemobile_prefs", MODE_PRIVATE)
                        .getString("ConnectionMode", "normal") == "proxy"
                    if (useProxy) {
                        LocalProxyManager.init(this@SplashActivity)
                        LocalProxyManager.start()
                        LocalProxyManager.isCAInstalled()
                    } else {
                        LocalProxyManager.stop()
                        true
                    }
                }
                certInstalled = installed
                checkDone = true
                checking = false
            }

            LaunchedEffect(certInstalled, checkDone) {
                if (checkDone && certInstalled && !exiting) {
                    exiting = true
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }

            SpotifyTheme {
                Box {
                    when {
                        checking -> LoadingScreen()
                        !certInstalled -> {
                            var certAlpha by remember { mutableStateOf(0f) }
                            LaunchedEffect(Unit) {
                                animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = tween(1300, easing = LinearEasing)
                                ) { value, _ -> certAlpha = value }
                            }
                            CACertScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .graphicsLayer { alpha = certAlpha },
                                onSwitchNormal = {
                                    getSharedPreferences("musemobile_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("ConnectionMode", "normal")
                                        .putBoolean("ServiceOn", false)
                                        .apply()
                                    LocalProxyManager.stop()
                                    recreate()
                                },
                                onCheck = {
                                    checking = true
                                    scope.launch {
                                        val installed = withContext(Dispatchers.IO) {
                                            if (!LocalProxyManager.isRunning) {
                                                LocalProxyManager.start()
                                                delay(500)
                                            }
                                            LocalProxyManager.isCAInstalled()
                                        }
                                        certInstalled = installed
                                        checking = false
                                    }
                                },
                                onExport = {
                                    scope.launch {
                                        val path = withContext(Dispatchers.IO) {
                                            LocalProxyManager.exportCACert(this@SplashActivity)
                                        }
                                        Toast.makeText(
                                            this@SplashActivity,
                                            "Exported to: $path",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val trackColor = Color.White.copy(alpha = 0.15f)
    val barColor = Color.White
    val totalDuration = 2000
    val holdDuration = 150
    val slideDuration = totalDuration - holdDuration * 2

    val infiniteTransition = rememberInfiniteTransition(label = "slide")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = totalDuration
                0f at 0
                0f at holdDuration
                1f at holdDuration + slideDuration using LinearEasing
                1f at totalDuration
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val versionName = remember { BuildConfig.VERSION_NAME }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(trackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.25f)
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = size.width * 4f * progress - size.width
                        }
                        .background(barColor)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "v$versionName",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CACertScreen(
    modifier: Modifier = Modifier,
    onSwitchNormal: () -> Unit,
    onCheck: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Certificate Required",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Install the MuseMobile CA certificate to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Step(1, "Export the .pem certificate below")
                Spacer(Modifier.height(16.dp))
                Step(2, "Settings > Security > Install a certificate > CA certificate")
                Spacer(Modifier.height(16.dp))
                Step(3, "Select the exported file and tap \"Install anyway\"")
                Spacer(Modifier.height(16.dp))
                Step(4, "Return here and tap \"Check\"")
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Export", color = Color.White.copy(alpha = 0.7f))
                }
            }

            Surface(
                onClick = onCheck,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Check", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            onClick = onSwitchNormal,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Box(
                modifier = Modifier.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Switch to Normal", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            lineHeight = 18.sp
        )
    }
}
