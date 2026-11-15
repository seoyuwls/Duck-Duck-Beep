package com.example.chargesuggest.ui.duck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.Rarity

/** 누르면 95%로 줄어드는 효과 (문서 3.6 버튼 인터랙션) */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    return this.graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
}

/** Basic Button. filled=true면 강조(채움), 아니면 옅은 배경 */
@Composable
fun BasicButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    height: Dp = 96.dp,
) {
    val c = duckColors
    val source = remember { MutableInteractionSource() }
    val bg = when {
        !enabled -> c.surfaceHigh
        filled -> c.buttonFilled
        else -> c.surfaceHigh
    }
    val fg = when {
        !enabled -> c.textTertiary
        filled -> c.onButtonFilled
        else -> c.textPrimary
    }
    Box(
        modifier = modifier
            .height(height)
            .pressScale(source)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DuckType.bodyLarge, color = fg, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
    }
}

/** Segmented Menu(탭). 선택된 칸은 채움 + 굵은 글자, 라벨은 항상 텍스트 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = duckColors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (on) c.buttonFilled else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = DuckType.bodyMedium, color = if (on) c.onButtonFilled else c.textSecondary)
            }
        }
    }
}

/** 주행 상태 표시: 색에만 의존하지 않고 글자도 함께 쓴다 */
@Composable
fun StatusChip(mode: DriveMode, modifier: Modifier = Modifier) {
    val c = duckColors
    val (label, dot) = when (mode) {
        DriveMode.DRIVING -> "주행 중 (D)" to c.informativeNegative
        DriveMode.STOPPED -> "정차 중" to c.regulationYellow
        DriveMode.PARKED -> "주차 중 (P)" to c.informativeActive
    }
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceHigh).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(dot))
        Text(label, style = DuckType.bodyMedium, color = c.textPrimary)
    }
}

/** 등급 표시색. 토큰에 없는 청록은 확인 필요. 항상 등급 이름 글자와 함께 쓴다 */
@Composable
fun rarityColor(r: Rarity): Color {
    val c = duckColors
    return when (r) {
        Rarity.COMMON -> c.basic400
        Rarity.RARE -> Color(0xFF00A5BD) // 확인 필요: 디자인 토큰에 청록이 없어 임시값
        Rarity.EPIC -> c.informativePositive
        Rarity.LEGENDARY -> c.informativeAutonomousDriving
    }
}

@Composable
fun RarityTag(r: Rarity, modifier: Modifier = Modifier) {
    val col = rarityColor(r)
    Text(
        r.label, style = DuckType.labelMedium, color = col,
        modifier = modifier.border(2.dp, col, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/**
 * 포니 이미지.
 *  - res/drawable 에 `pony_basic.png` 처럼 인형 id와 같은 이름의 이미지가 있으면 그것을 쓴다 (직접 만든 이미지).
 *  - 없으면 아래 자리표시용 도형(조랑말 모양)을 그린다.
 * 등급이 올라갈수록 꾸밈(모자, 선글라스, 왕관)이 붙고, 상황 인형은 소품으로 구분한다.
 * silhouette=true면 아직 받지 못한 인형으로 회색 실루엣만 보여 준다.
 */
@Composable
fun DuckImage(kind: DuckKind, size: Dp, modifier: Modifier = Modifier, silhouette: Boolean = false) {
    val context = LocalContext.current
    val resId = remember(kind.id) { context.resources.getIdentifier(kind.id, "drawable", context.packageName) }
    if (resId != 0) {
        Image(
            painter = painterResource(resId), contentDescription = kind.name, modifier = modifier.size(size),
            colorFilter = if (silhouette) ColorFilter.tint(Color(0xFF9AA0A6), BlendMode.SrcIn) else null,
        )
    } else {
        Canvas(modifier.size(size)) { drawPony(kind.id, silhouette) }
    }
}

// ---- 자리표시용 포니 그림 (docs/pony-concept.svg 를 100x100 좌표 그대로 옮긴 것) ----
private val PonyBlue = Color(0xFF1F4E9E)      // 몸 (네이비 패브릭)
private val PonyBlueDark = Color(0xFF173B78)  // 먼 쪽 다리
private val PonyWhite = Color(0xFFFBFBF7)     // 주둥이, 발굽, 갈기, 꼬리
private val PonyFarHoof = Color(0xFFE6EAE8)
private val PonyOutline = Color(0xFFD3DCE5)   // 흰 털이 흰 배경에서도 보이게 하는 테두리
private val PonyStitch = Color(0xFF5B86C9)    // 네이비 위에서 보이도록 밝은 파랑
private val Ink = Color(0xFF0A0F1F)           // 눈 (아주 짙은 남색 자수)

/** 3D 뷰어와 상세 화면의 배경. 테마와 상관없이 밝은 색으로 고정해 네이비 포니가 어두운 테마에서도 보이게 한다 */
val PonyStage = Color(0xFFEDEEF2)

/**
 * 자리표시용 조랑말(오른쪽을 보는 옆모습): 하늘색 천 몸, 흰 주둥이·발굽, 푹신한 흰 갈기와 꼬리.
 * 직접 만든 이미지(res/drawable/pony_*.png)가 생기면 쓰이지 않는다.
 */
private fun DrawScope.drawPony(id: String, silhouette: Boolean) {
    val s = this.size.minDimension
    fun u(v: Float) = v / 100f * s
    fun p(x: Float, y: Float) = Offset(u(x), u(y))
    fun rr(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(c, p(x, y), Size(u(w), u(h)), androidx.compose.ui.geometry.CornerRadius(u(r)))
    fun ci(c: Color, x: Float, y: Float, r: Float) = drawCircle(c, u(r), p(x, y))

    val gray = Color(0xFF9AA0A6)
    fun tone(c: Color) = if (silhouette) gray else c
    val blue = tone(PonyBlue); val blueDark = tone(PonyBlueDark); val white = tone(PonyWhite)
    val farHoof = tone(PonyFarHoof); val outline = tone(PonyOutline)

    if (id == "pony_stars" && !silhouette) {
        drawCircle(Color(0xFF1A237E), radius = s / 2, center = p(50f, 50f))
        listOf(20f to 18f, 82f to 20f, 12f to 60f, 88f to 62f).forEach { (x, y) -> ci(Color.White, x, y, 2f) }
    }

    // 뒤쪽(먼 쪽) 다리
    rr(blueDark, 34f, 62f, 12f, 25f, 5f); rr(farHoof, 34f, 77f, 12f, 10f, 4.5f)
    rr(blueDark, 58f, 62f, 12f, 25f, 5f); rr(farHoof, 58f, 77f, 12f, 10f, 4.5f)

    // 꼬리 (테두리 원을 먼저 깔고 흰 원을 위에)
    listOf(Triple(19f, 50f, 8f), Triple(13f, 58f, 7.2f), Triple(18f, 66f, 7f), Triple(24f, 57f, 7.6f)).forEach { (x, y, r) ->
        ci(outline, x, y, r + 1f); }
    listOf(Triple(19f, 50f, 8f), Triple(13f, 58f, 7.2f), Triple(18f, 66f, 7f), Triple(24f, 57f, 7.6f)).forEach { (x, y, r) ->
        ci(white, x, y, r)
    }

    // 몸통과 배 쪽 솔기
    rr(blue, 20f, 42f, 58f, 30f, 13f)
    if (!silhouette) {
        val seam = Path().apply { moveTo(u(44f), u(71f)); quadraticBezierTo(u(50f), u(66f), u(56f), u(71f)) }
        drawPath(seam, PonyStitch, style = androidx.compose.ui.graphics.drawscope.Stroke(u(0.8f), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }

    // 앞쪽(가까운 쪽) 다리와 흰 발굽
    rr(blue, 26f, 62f, 13f, 25f, 5.5f); rr(white, 26f, 76f, 13f, 11f, 5f)
    rr(blue, 64f, 62f, 13f, 25f, 5.5f); rr(white, 64f, 76f, 13f, 11f, 5f)

    // 갈기
    val mane = listOf(Triple(66f, 13f, 6.4f), Triple(58f, 22f, 6.4f), Triple(55f, 32f, 6.4f), Triple(58f, 42f, 5.4f), Triple(73f, 10f, 4.8f))
    mane.forEach { (x, y, r) -> ci(outline, x, y, r + 1f) }
    mane.forEach { (x, y, r) -> ci(white, x, y, r) }

    // 목, 머리
    rr(blue, 58f, 30f, 22f, 26f, 9f)
    rr(blue, 62f, 16f, 32f, 30f, 12f)

    // 귀: 하늘색 바깥, 흰 안쪽
    val ear = Path().apply {
        moveTo(u(66f), u(21f)); quadraticBezierTo(u(65f), u(7f), u(76f), u(4f)); quadraticBezierTo(u(81f), u(13f), u(80f), u(21f)); close()
    }
    drawPath(ear, blue)
    val earIn = Path().apply {
        moveTo(u(69f), u(20f)); quadraticBezierTo(u(69f), u(11f), u(75.5f), u(8.5f)); quadraticBezierTo(u(78f), u(14f), u(77.5f), u(20f)); close()
    }
    drawPath(earIn, white)

    // 주둥이와 콧구멍, 눈
    rr(white, 79f, 30f, 18f, 17f, 8.5f)
    if (!silhouette) {
        drawLine(PonyStitch, p(91.5f, 37f), p(92f, 39.6f), strokeWidth = u(0.9f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(PonyStitch, p(94.6f, 37.4f), p(94.8f, 39.8f), strokeWidth = u(0.9f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawOval(Ink, p(77f, 21.7f), Size(u(6f), u(7.6f)))
    }

    if (silhouette) return
    // 꾸밈과 상황 소품 (시연에서는 기본형만 쓰지만 구조는 남겨 둔다)
    when (id) {
        "pony_hat" -> { rr(Color(0xFFD32F2F), 79f, 14f, 16f, 3.5f, 1.5f); rr(Color(0xFFD32F2F), 82f, 6f, 10f, 9f, 2f) }
        "pony_sunglasses" -> rr(Ink, 74f, 22f, 15f, 7f, 3f)
        "pony_crown" -> {
            val crown = Path().apply {
                moveTo(u(80f), u(16f)); lineTo(u(81f), u(6f)); lineTo(u(85f), u(11f)); lineTo(u(87f), u(4f))
                lineTo(u(89f), u(11f)); lineTo(u(93f), u(6f)); lineTo(u(94f), u(16f)); close()
            }
            drawPath(crown, Color(0xFFFFC107))
        }
        "pony_umbrella" -> {
            drawArc(Color(0xFF1976D2), 180f, 180f, true, p(60f, 0f), Size(u(40f), u(16f)))
            drawLine(Ink, p(80f, 8f), p(80f, 16f), strokeWidth = u(1f))
        }
        "pony_plug" -> {
            ci(Color(0xFF2E7D32), 88f, 82f, 8f)
            drawLine(Color.White, p(85.5f, 77f), p(85.5f, 82f), strokeWidth = u(1.6f))
            drawLine(Color.White, p(90.5f, 77f), p(90.5f, 82f), strokeWidth = u(1.6f))
            drawRect(Color.White, p(84f, 82f), Size(u(9f), u(3f)))
        }
        "pony_family" -> { // 새끼 포니
            drawOval(PonyBlue, p(46f, 73f), Size(u(13f), u(10f)))
            ci(PonyBlue, 60f, 75f, 3.6f)
            ci(Ink, 61.4f, 74.2f, 0.8f)
        }
    }
}
