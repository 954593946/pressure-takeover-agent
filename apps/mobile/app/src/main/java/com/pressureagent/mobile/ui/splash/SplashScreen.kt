package com.pressureagent.mobile.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pressureagent.mobile.BuildConfig
import com.pressureagent.mobile.R
import com.pressureagent.mobile.ui.theme.AuriGold
import com.pressureagent.mobile.ui.theme.AuriNavy
import kotlinx.coroutines.delay

/**
 * Splash screen — brief brand moment, then enter the app.
 *
 * Health check runs in background but does NOT block entry.
 * Connection state is handled by the repository's SSE/polling fallback in the main UI.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splash_alpha",
    )

    // Validate config — only block if URL or token is literally missing
    val configError = remember {
        when {
            BuildConfig.AGENT_API_BASE_URL.isBlank() ->
                "未配置 API 地址\n请在 build.gradle.kts 中设置 AGENT_API_BASE_URL"
            BuildConfig.AGENT_API_TOKEN.isBlank() && !BuildConfig.USE_MOCK_AGENT ->
                "未配置 API Token\n请在 build.gradle.kts 中设置 AGENT_API_TOKEN"
            else -> null
        }
    }

    LaunchedEffect(Unit) {
        startAnimation = true
        if (configError == null) {
            delay(2000)
            onSplashFinished()
        }
        // If config is broken, stay on splash and show error — user must fix and rebuild
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuriNavy),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alphaAnim).padding(32.dp),
        ) {
            // Logo image
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(180.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "AURI",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "你只管开，我来处理",
                color = AuriGold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Config error — only shown if URL/token is literally empty
            if (configError != null) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Red.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ 配置错误", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            configError ?: "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (BuildConfig.USE_MOCK_AGENT) {
                Text(
                    "Mock 模式 · 离线演示",
                    color = AuriGold.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    BuildConfig.AGENT_API_BASE_URL,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
