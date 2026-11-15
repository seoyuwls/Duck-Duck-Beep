package com.example.chargesuggest.ui.duckduck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 화면에 나타날 때의 모션: 살짝 아래에서 올라오며 흐리게 나타난다(0.3초, 과하지 않게).
 * index가 크면 그만큼 늦게 시작해서 여러 칸이 차례로 나타난다(칸당 0.05초).
 */
@Composable
fun Modifier.appear(index: Int = 0, rise: Dp = 14.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * risePx
    }
}
