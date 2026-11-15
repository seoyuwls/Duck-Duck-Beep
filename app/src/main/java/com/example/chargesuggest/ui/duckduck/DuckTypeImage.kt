package com.example.chargesuggest.ui.duckduck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.chargesuggest.duckduck.domain.DuckType

/**
 * 오리 이미지.
 *  - res/drawable-nodpi 에 `duck_basic.png`처럼 `duck_<오리 id>` 이름의 이미지가 있으면 그것을 쓴다 (직접 만든 이미지).
 *  - 없으면 아래 자리표시용 도형을 그린다.
 * silhouette=true면 아직 받지 못한 오리로 회색 실루엣만 보여 준다.
 * 오리 id: rainbow, snowman, raincoat, umbrella, sunglasses, scarf, basic
 */
@Composable
fun DuckTypeImage(type: DuckType, size: Dp, modifier: Modifier = Modifier, silhouette: Boolean = false) {
    val context = LocalContext.current
    // 아직 받지 못한 오리(silhouette)는 "?" 오리 그림(duck_unknown)이 있으면 그것을 보여 준다. 없으면 오리 그림에 회색을 입힌다
    val unknownId = remember { context.resources.getIdentifier("duck_unknown", "drawable", context.packageName) }
    val resId = remember(type, silhouette) {
        if (silhouette && unknownId != 0) unknownId
        else context.resources.getIdentifier("duck_${type.id}", "drawable", context.packageName)
    }
    val tint = silhouette && unknownId == 0
    if (resId != 0) {
        Image(
            painter = painterResource(resId), contentDescription = type.label, modifier = modifier.size(size),
            colorFilter = if (tint) ColorFilter.tint(Color(0xFF9AA0A6), BlendMode.SrcIn) else null,
        )
    } else {
        Canvas(modifier.size(size)) { drawDuck(type, silhouette) }
    }
}

private val DuckYellow = Color(0xFFFFD54F)
private val DuckOrange = Color(0xFFFF9800)
private val Ink = Color(0xFF263238)

/** 자리표시용 오리(오른쪽을 보는 옆모습). 실제 이미지가 생기면 쓰이지 않는다 */
private fun DrawScope.drawDuck(type: DuckType, silhouette: Boolean) {
    val s = this.size.minDimension
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    val gray = Color(0xFF9AA0A6)
    val body = when {
        silhouette -> gray
        type == DuckType.SNOWMAN -> Color(0xFFF5F5F5)
        else -> DuckYellow
    }
    val beak = if (silhouette) gray else DuckOrange

    // 무지개 오리는 뒤에 무지개를 깐다
    if (type == DuckType.RAINBOW && !silhouette) {
        val colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
        colors.forEachIndexed { i, c ->
            val inset = 0.04f * i
            drawArc(c, 180f, 180f, false, p(0.05f + inset, 0.05f + inset), sz(0.9f - 2 * inset, 0.9f - 2 * inset),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.04f * s))
        }
    }

    // 몸, 날개, 머리, 부리, 눈
    drawOval(body, p(0.15f, 0.45f), sz(0.6f, 0.45f))
    drawOval(if (silhouette) gray else if (type == DuckType.SNOWMAN) Color(0xFFE0E0E0) else Color(0xFFFFB300), p(0.25f, 0.58f), sz(0.3f, 0.18f))
    drawCircle(body, 0.2f * s, p(0.6f, 0.34f))
    drawOval(beak, p(0.74f, 0.33f), sz(0.2f, 0.1f))
    drawCircle(if (silhouette) gray else Ink, 0.025f * s, p(0.64f, 0.3f))

    if (silhouette) return
    when (type) {
        DuckType.UMBRELLA -> { // 우산
            drawArc(Color(0xFF1976D2), 180f, 180f, true, p(0.3f, -0.02f), sz(0.6f, 0.34f))
            drawLine(Ink, p(0.6f, 0.15f), p(0.6f, 0.2f), strokeWidth = 0.015f * s)
        }
        DuckType.RAINCOAT -> { // 노란 우비 + 모자
            drawOval(Color(0xFFF9A825), p(0.17f, 0.5f), sz(0.56f, 0.4f))
            drawArc(Color(0xFFF9A825), 180f, 180f, true, p(0.42f, 0.1f), sz(0.36f, 0.3f))
        }
        DuckType.SUNGLASSES -> // 선글라스
            drawRoundRect(Ink, p(0.55f, 0.27f), sz(0.2f, 0.07f), cornerRadius = CornerRadius(0.02f * s))
        DuckType.SCARF -> { // 목도리
            drawRoundRect(Color(0xFFD32F2F), p(0.46f, 0.46f), sz(0.3f, 0.07f), cornerRadius = CornerRadius(0.03f * s))
            drawRoundRect(Color(0xFFD32F2F), p(0.5f, 0.5f), sz(0.07f, 0.16f), cornerRadius = CornerRadius(0.02f * s))
        }
        DuckType.SNOWMAN -> { // 검은 모자 + 당근 코
            drawRect(Ink, p(0.5f, 0.06f), sz(0.2f, 0.1f))
            drawRect(Ink, p(0.45f, 0.15f), sz(0.3f, 0.03f))
            drawPath(Path().apply { moveTo(0.74f * s, 0.34f * s); lineTo(0.95f * s, 0.38f * s); lineTo(0.74f * s, 0.41f * s); close() }, Color(0xFFFF7043))
        }
        DuckType.RAINBOW, DuckType.BASIC -> Unit
    }
}
