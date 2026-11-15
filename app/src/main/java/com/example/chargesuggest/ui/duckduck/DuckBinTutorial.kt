package com.example.chargesuggest.ui.duckduck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duckduck.DuckDuckController
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.duckColors

/*
 * 처음 시작 튜토리얼(메인 화면 아래쪽 안내, 3단계). 첫 사용자가 처음 메인에 들어왔을 때 한 번만 나온다.
 * 화면 아래쪽이 위에서 아래로 어두워지는 띠(그라데이션)가 되고, 그 위에 가운데 정렬로 안내가 뜬다. 버튼은 없다: 안내대로 해 보면 다음으로 넘어간다.
 *   1  주행 중 같은 차를 만나면 오리를 건네주세요!  / 오리를 집어서 던지듯 날려보세요   -> 오리를 위로 던지면 다음
 *   2  내가 받은 오리도 실시간으로 나타나요        / 빛나는 오리를 눌러서 확인해보세요   -> 왼쪽에서 오리가 굴러 들어오고, 그 오리를 눌러 확인하면 다음
 *   3  받은 오리는 전부 내 도감에 기록돼요        / 우측 상단의 책 아이콘을 눌러보세요 -> 도감을 열면 다음(책 아이콘에 노란 고리가 깜빡인다)
 *   완료  도감에 다녀오면 "튜토리얼이 끝났어요 / 즐거운 주행 되세요!"가 잠깐(약 3초) 나온 뒤 사라지고 끝난다
 * 타이밍(안내 하나): 이벤트 -> 앞 안내가 사라짐 -> 잠깐 쉼 -> 그라데이션이 부드럽게 깔림(0.7초) -> 0.45초 뒤 글자가 나타남(0.45초) -> 다음 이벤트.
 * 첫 안내는 오리가 다 떨어진 뒤(2.6초) 시작한다.
 * 2단계의 받은 오리도 안내와 같은 순간에 굴러 들어온다. 띠는 터치를 막지 않아서 아래의 오리도 그대로 만질 수 있다.
 */

/** 튜토리얼 안내를 보여 줄 때인가: 주행 중이거나 다른 카드가 떠 있으면 숨긴다 */
internal fun tutorialVisible(state: DuckDuckState): Boolean =
    state.tutorialStep in 1..4 && (state.mode != DriveMode.DRIVING || DuckConfig.ONBOARD_WHILE_DRIVING) && state.card == null && state.pendingSend == null

/** 메인에 들어온 뒤 첫 안내가 나오기까지(오리가 떨어지는 동안)와 단계 사이의 쉬는 시간 */
private const val FIRST_DELAY_MS = 2600L
private const val STEP_DELAY_MS = 1400L

/** 2단계(받은 오리 확인) 뒤 3단계로 넘어갈 때는 조금 짧게 쉰다 */
private const val SHORT_STEP_DELAY_MS = 700L

/** 도감에서 돌아온 뒤 "튜토리얼 완료" 안내가 나오기까지, 그리고 안내를 보여 주는 시간 */
private const val DONE_DELAY_MS = 700L
private const val DONE_HOLD_MS = 2800L

/** 그라데이션이 깔리는 시간, 그 뒤 글자가 나타나기까지 기다리는 시간, 글자가 나타나는 시간 */
private const val GRADIENT_IN_MS = 700
private const val TEXT_AFTER_GRADIENT_MS = 450L
private const val TEXT_IN_MS = 450

private val HighlightYellow = Color(0xFFFFC224)
private val HintGray = Color(0xFFD9D9D9)

@Composable
internal fun TutorialOverlay(state: DuckDuckState, controller: DuckDuckController, onCollection: () -> Unit) {
    val step = state.tutorialStep
    val visible = tutorialVisible(state)
    // 이벤트(오리 던지기 등)가 일어나면: 앞 안내가 사라지고 -> (쉬었다가) 그라데이션이 부드럽게 깔리고 -> 그 위로 글자가 나타난다
    var gradientOn by remember(step) { mutableStateOf(false) }
    var textOn by remember(step) { mutableStateOf(false) }
    var shownStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(step, visible) {
        if (!visible) { gradientOn = false; textOn = false; return@LaunchedEffect }
        if (!gradientOn) {
            kotlinx.coroutines.delay(when (step) { 1 -> FIRST_DELAY_MS; 3 -> SHORT_STEP_DELAY_MS; 4 -> DONE_DELAY_MS; else -> STEP_DELAY_MS })
            // 2단계: 그라데이션이 깔리기 시작하는 순간에 받은 오리가 왼쪽에서 굴러 들어온다
            if (step == 2) controller.deliverTutorialDuck()
            shownStep = step
            gradientOn = true
        }
        kotlinx.coroutines.delay(TEXT_AFTER_GRADIENT_MS)
        textOn = true
        // 도감에 다녀온 뒤 "튜토리얼 완료" 안내를 잠깐 보여 주고 끝낸다
        if (step == 4) {
            kotlinx.coroutines.delay(DONE_HOLD_MS)
            controller.finishTutorial()
        }
    }
    val gradientAlpha by animateFloatAsState(if (gradientOn) 1f else 0f, tween(if (gradientOn) GRADIENT_IN_MS else 260, easing = FastOutSlowInEasing), label = "tutorial-gradient")
    val textAlpha by animateFloatAsState(if (textOn) 1f else 0f, tween(if (textOn) TEXT_IN_MS else 200, easing = FastOutSlowInEasing), label = "tutorial-text")
    if (gradientAlpha <= 0.001f && textAlpha <= 0.001f) return
    val shown = if (shownStep in 1..4) shownStep else step
    val c = duckColors
    val pulse by rememberInfiniteTransition(label = "tutorial").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (shown == 3 && textAlpha > 0f) {
            // 도감 아이콘 자리(머리글 오른쪽, 설정 아이콘 왼쪽)에서 깜빡이는 노란 고리
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 40.dp + 64.dp + HEADER_ICON_GAP)
                    .size(64.dp).alpha(textAlpha).scale(1f + 0.18f * pulse)
                    .border(4.dp, c.regulationYellow.copy(alpha = 0.4f + 0.6f * pulse), CircleShape)
                    // 도감 아이콘 위에 오리가 쌓여 있어도 이 고리를 누르면 도감이 열린다
                    .clip(CircleShape).background(Color.White).clickable(onClick = onCollection),
                contentAlignment = Alignment.Center,
            ) {
                // 오리가 책 아이콘을 가리고 있어도 보이도록, 이 안내 단계에서는 아이콘을 오리 위에 흰 원과 함께 다시 그린다
                PhIcon(com.example.chargesuggest.R.drawable.ph_book_bookmark, c.basic600, size = 40.dp)
            }
        }
        // 아래쪽 어두운 띠: 위는 투명, 아래로 갈수록 검정에 가까워진다(터치는 막지 않는다). 부드럽게 깔린다
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * 0.42f).alpha(gradientAlpha)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0xB3000000), 1f to Color(0xF2000000))),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = maxHeight * 0.11f, start = 40.dp, end = 40.dp).alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (shown != 4) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("$shown") }
                        withStyle(SpanStyle(color = Color(0xFFB0B0B0))) { append(" / 3") }
                    },
                    style = Type.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
            }
            Text(headline(shown), style = Type.bodyLarge.copy(lineHeight = 46.sp, fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
            if (shown != 4) {
                Spacer(Modifier.height(12.dp))
                Text(hint(shown), style = Type.bodySmall, color = HintGray, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun headline(step: Int) = when (step) {
    1 -> "주행 중 같은 차를 만나면\n오리를 건네주세요!"
    2 -> "내가 받은 오리도\n실시간으로 나타나요"
    4 -> "튜토리얼이 끝났어요\n즐거운 주행 되세요!"
    else -> "받은 오리는 전부\n내 도감에 기록돼요"
}

/** 힌트 한 줄: 행동 부분(노란색)을 강조한다 */
private fun hint(step: Int) = buildAnnotatedString {
    val y = SpanStyle(color = HighlightYellow)
    when (step) {
        1 -> { append("오리를 "); withStyle(y) { append("집어서 던지듯") }; append(" 날려보세요") }
        2 -> { append("빛나는 오리를 "); withStyle(y) { append("눌러서 확인") }; append("해보세요") }
        4 -> Unit
        else -> { append("우측 상단의 "); withStyle(y) { append("책 아이콘") }; append("을 눌러보세요") }
    }
}
