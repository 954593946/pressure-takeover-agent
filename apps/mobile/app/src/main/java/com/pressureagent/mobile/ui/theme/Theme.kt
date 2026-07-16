package com.pressureagent.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AURI Brand Tokens — see root README.md
//   --auri-navy:    #0B1B33  主品牌色、深色背景、标题
//   --auri-gold:    #D4AF7A  Logo 光环、品牌强调
//   --auri-ivory:   #F5F2EC  浅色背景、卡片底
//   State colors:
//     processing:   #2F6BFF  驾驶连接、规划、接管处理中
//     warning:       #E6A700  L1 时间窗口压缩与待注意
//     success:       #2E9D6F  已完成、已同步、恢复态
//     critical:      #D1495B  L3 高负荷保护、错误

val AuriNavy = Color(0xFF0B1B33)
val AuriGold = Color(0xFFD4AF7A)
val AuriIvory = Color(0xFFF5F2EC)
val AuriProcessing = Color(0xFF2F6BFF)
val AuriWarning = Color(0xFFE6A700)
val AuriSuccess = Color(0xFF2E9D6F)
val AuriCritical = Color(0xFFD1495B)

private val AuriLightColors = lightColorScheme(
    primary = AuriNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = AuriGold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECC2),
    onTertiaryContainer = Color(0xFF2A2000),
    error = AuriCritical,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = AuriIvory,
    onBackground = AuriNavy,
    surface = AuriIvory,
    onSurface = AuriNavy,
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
)

@Composable
fun PressureTakeoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuriLightColors,
        typography = Typography(),
        content = content,
    )
}
