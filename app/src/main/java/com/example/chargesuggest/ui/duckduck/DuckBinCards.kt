package com.example.chargesuggest.ui.duckduck

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargesuggest.duckduck.Card
import com.example.chargesuggest.duckduck.DuckDuckController
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.domain.Josa
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.duckColors

/*
 * 오리 통 화면 위에 잠깐 뜨는 카드 세 가지(시안 2·3·5번). 뒤의 오리 더미는 그대로 보인다.
 *   보낼까요?      던진 오리가 화면 밖으로 나가 근처에 보낼 수 있는 같은 차가 있을 때. [돌아가기] / [보내기]. 답이 없으면 10초 뒤 닫힌다
 *   오리를 보냈어요! 보낸 뒤 3초 동안. 저절로 사라진다
 *   좋은 하루 되세요! 받은 오리를 확인할 때. [확인]을 눌러야 닫힌다
 * 카드가 떠 있는 동안(보낼까요?, 받은 오리 확인) 뒤의 오리는 만질 수 없다. 오른쪽 위 도감·설정 아이콘은 카드 위에 그대로 있다.
 * 크기와 위치는 시안(2000×1440)의 비율을 따른다: 너비 32%, 위에서 25% 아래.
 */


@Composable
internal fun BinCards(state: DuckDuckState, controller: DuckDuckController) {
    val pending = state.pendingSend
    val card = state.card
    // 카드가 바뀔 때 부드럽게 나타나고 사라진다(살짝 커지며 흐려짐, 0.2초)
    androidx.compose.animation.AnimatedContent(
        targetState = Pair(pending, card),
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260, delayMillis = 60), initialScale = 0.94f)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
        },
        label = "bin-card",
    ) { (pending, card) ->
    when {
        pending != null -> CardLayer(blockTouches = true, anchorTop = true) { w ->
            SendCard(w, state.modelName, pending.type.label, pending.type, onBack = { controller.cancelSend() }, onSend = { controller.confirmSend() })
        }
        card is Card.Result -> CardLayer(blockTouches = true, anchorTop = true) { w ->
            ReceivedCard(w, card.entry.type.message, "${card.entry.fromNickname} · ${card.entry.type.label}", card.entry.type, onConfirm = { controller.dismissCard() })
        }
        card is Card.Sent -> CardLayer(blockTouches = false, anchorTop = false) { w ->
            SentCard(w, "상대 ${Josa.subject(state.modelName)} 좋아할 것 같아요")
        }
    }
    }
}

/** 카드를 놓는 층. blockTouches면 뒤의 오리로 가는 터치를 막는다 */
@Composable
private fun CardLayer(blockTouches: Boolean, anchorTop: Boolean, content: @Composable (cardWidth: Dp) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().then(
            if (blockTouches) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }
            else Modifier,
        ),
    ) {
        val cardWidth = (maxWidth * 0.32f).coerceIn(420.dp, 560.dp).coerceAtMost(maxWidth * 0.92f)
        Box(
            Modifier.fillMaxSize().padding(top = if (anchorTop) maxHeight * 0.25f else 0.dp),
            contentAlignment = if (anchorTop) Alignment.TopCenter else Alignment.Center,
        ) { content(cardWidth) }
    }
}

/**
 * 시안(Figma)의 카드 그림자: 오른쪽 아래로 X 74.57, Y 39.78, 흐림 198.9, 퍼짐 0, 색 #091432 15%.
 * 시안의 카드 너비(640)에 대한 비율(scale)로 줄여서 쓴다. Compose의 elevation 그림자는 위치·흐림을 정할 수 없어서 직접 그린다.
 * 흐림 값은 Figma의 번짐 반경이라 안드로이드 그림자 반지름으로는 약 0.87배다.
 */
private fun Modifier.figmaShadow(scale: Float, cornerRadius: Dp): Modifier = drawBehind {
    val dx = (74.57f * scale).dp.toPx()
    val dy = (39.78f * scale).dp.toPx()
    val blur = (198.9f * scale * 0.866f).dp.toPx()
    val r = cornerRadius.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            setShadowLayer(blur, dx, dy, FigmaShadowColor.toArgb())
        }
        // 카드가 반투명이라 카드 안쪽에는 그림자를 그리지 않는다(카드 모양 안을 잘라 낸다)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r, r)) }, androidx.compose.ui.graphics.ClipOp.Difference)
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        canvas.restore()
    }
}

private val FigmaShadowColor = Color(0x26091432) // #091432, 15%

/** 카드 채움 투명도: 시안의 Fill FFFFFF 85% */
private const val CARD_FILL_ALPHA = 0.85f

@Composable
private fun CardFrame(width: Dp, content: @Composable () -> Unit) {
    val c = duckColors
    val shape = RoundedCornerShape(34.dp)
    Column(
        Modifier.width(width)
            .figmaShadow(scale = width.value / 640f, cornerRadius = 34.dp)
            // 시안의 유리 카드: 흰색 85% 채움이라 뒤의 오리가 은은하게 비친다(굴절·분산 효과는 내지 않는다)
            .clip(shape).background(c.basic00.copy(alpha = CARD_FILL_ALPHA)).padding(horizontal = 40.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** 디자인 시스템의 채운 버튼(button_filled = basic_600, 글자 basic_00) */
@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = duckColors
    Box(
        modifier.height(90.dp).clip(RoundedCornerShape(16.dp)).background(c.buttonFilled).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.bodyLarge.copy(fontWeight = FontWeight.Bold), color = c.onButtonFilled) }
}

/** 2번: 근처 ○○에게 ○○ 오리를 보낼까요? */
@Composable
private fun SendCard(width: Dp, modelName: String, typeLabel: String, type: com.example.chargesuggest.duckduck.domain.DuckType, onBack: () -> Unit, onSend: () -> Unit) {
    val c = duckColors
    CardFrame(width) {
        val josa = Josa.obj(typeLabel).removePrefix(typeLabel)
        Text(
            "근처 ${modelName}에게\n${typeLabel}${josa} 보낼까요?",
            style = Type.bodyLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 44.sp), color = c.textPrimary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        DuckTypeImage(type, width - 80.dp - 60.dp)
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.height(90.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("돌아가기", style = Type.bodyMedium, color = c.textSecondary) }
            ActionButton("보내기", onSend, Modifier.weight(1f))
        }
    }
}

/** 3번: 오리를 보냈어요! */
@Composable
private fun SentCard(width: Dp, sub: String) {
    val c = duckColors
    CardFrame(width) {
        val checkYellow = c.regulationYellow
        Canvas(Modifier.size(64.dp)) {
            drawCircle(checkYellow)
            val p = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.36f)
            }
            drawPath(p, Color.White, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(24.dp))
        Text("오리를 보냈어요!", style = Type.bodyLarge.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(sub, style = Type.bodySmall, color = c.basic600, textAlign = TextAlign.Center)
    }
}

/** 5번: 받은 오리 확인 */
@Composable
private fun ReceivedCard(width: Dp, message: String, sub: String, type: com.example.chargesuggest.duckduck.domain.DuckType, onConfirm: () -> Unit) {
    val c = duckColors
    CardFrame(width) {
        Text(message, style = Type.bodyLarge.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(sub, style = Type.bodySmall, color = c.basic600, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        DuckTypeImage(type, width - 80.dp - 60.dp)
        Spacer(Modifier.height(36.dp))
        ActionButton("확인", onConfirm, Modifier.fillMaxWidth())
    }
}
