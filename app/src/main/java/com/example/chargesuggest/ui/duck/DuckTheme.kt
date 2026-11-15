package com.example.chargesuggest.ui.duck

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Pleos 디자인 시스템(docs/pleos-design-system.md)의 토큰을 코드로 옮긴 것.
 * 색은 토큰 이름 그대로, 라이트/다크 쌍으로 둔다. 문서에 없는 값은 "확인 필요"로 표시했다.
 * 폰트(Asta Sans)는 설치 방법이 확인되지 않아 시스템 기본 폰트를 쓴다. 확인 필요.
 */

@Immutable
class DuckColors(
    // basic_* (문서 3.3). 라이트와 다크가 서로 뒤집힌다
    val basic00: Color, val basic50: Color, val basic100: Color, val basic200: Color,
    val basic300: Color, val basic400: Color, val basic500: Color, val basic600: Color,
    val basic700: Color, val basic900: Color,
    // informative_*, regulation_*
    val informativeActive: Color,
    val informativePositive: Color,
    val informativeNegative: Color,
    val informativeAutonomousDriving: Color,
    val regulationYellow: Color,
    val regulationOrange: Color,
) {
    // 용도별 별칭 (배경·표면 표, 문서 3.3)
    val backgroundPrimary get() = basic50
    val surfaceBasic get() = basic00      // 라이트 기준. 다크는 basic_50이 문서값이지만 배경과 같아져 카드가 안 보이므로 아래에서 보정
    val surfaceHigh get() = basic100
    val surfaceAccent get() = basic200
    val textPrimary get() = basic900
    val textSecondary get() = basic500
    val textTertiary get() = basic400
    val buttonFilled get() = basic600
    val onButtonFilled get() = basic00
}

private val LightColors = DuckColors(
    basic00 = Color(0xFFFFFFFF), basic50 = Color(0xFFF7F8FA), basic100 = Color(0xFFEDEEF2), basic200 = Color(0xFFE0E1E6),
    basic300 = Color(0xFFC7C9CE), basic400 = Color(0xFFA4A8AE), basic500 = Color(0xFF686A72), basic600 = Color(0xFF44464E),
    basic700 = Color(0xFF313236), basic900 = Color(0xFF131417),
    informativeActive = Color(0xFF02C265), informativePositive = Color(0xFF0064FF),
    informativeNegative = Color(0xFFFE3D16), informativeAutonomousDriving = Color(0xFF5A46FA),
    regulationYellow = Color(0xFFFFC224), regulationOrange = Color(0xFFFF8A00),
)

private val DarkColors = DuckColors(
    basic00 = Color(0xFF131417), basic50 = Color(0xFF1D1E21), basic100 = Color(0xFF313236), basic200 = Color(0xFF44464E),
    basic300 = Color(0xFF686A72), basic400 = Color(0xFFA4A8AE), basic500 = Color(0xFFC7C9CE), basic600 = Color(0xFFE0E1E6),
    basic700 = Color(0xFFEDEEF2), basic900 = Color(0xFFFFFFFF),
    informativeActive = Color(0xFF00E074), informativePositive = Color(0xFF0082FF),
    informativeNegative = Color(0xFFFF4C28), informativeAutonomousDriving = Color(0xFF6A57FF),
    regulationYellow = Color(0xFFFFC224), regulationOrange = Color(0xFFFF8A00),
)

val LocalDuckColors = staticCompositionLocalOf { LightColors }

/** 타입 스케일 (문서 3.4). 행간은 1.5배 이상 권장이라 1.4배를 쓴다 [확인 필요: 스타일별 행간 수치는 문서에 없음] */
object DuckType {
    private fun style(size: Int, weight: FontWeight) =
        TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4f).sp)

    val headlineMedium = style(56, FontWeight.Bold)
    val titleLarge = style(40, FontWeight.Bold)
    val titleMedium = style(36, FontWeight.Bold)
    val titleSmall = style(32, FontWeight.Normal)
    val bodyLarge = style(30, FontWeight.SemiBold)
    val bodyMedium = style(28, FontWeight.Normal)
    val bodySmall = style(26, FontWeight.Normal)
    val labelMedium = style(24, FontWeight.Normal)
    val labelSmall = style(20, FontWeight.Normal)
}

@Composable
fun DuckTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalDuckColors provides colors, content = content)
}

val duckColors: DuckColors
    @Composable get() = LocalDuckColors.current
