package com.example.chargesuggest.ui.duckduck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargesuggest.R
import com.example.chargesuggest.duckduck.Card
import com.example.chargesuggest.duckduck.DuckDuckController
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.SendOutcome
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Josa
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.duckColors
import com.example.chargesuggest.ui.duck.pressScale

/*
 * 주행·정차 화면 (와이어프레임, 1682×1210 기준 측정값).
 *
 *   주행(MOVING)  3 찾는 중 / 4 발견(오리가 흐릿함, 눌러도 안 됨) / 받았어요 알림     -- 터치 조작 없음, 도감·설정 아이콘 없음
 *   정차(IDLING)  받은 오리 확인(자동, 3초) / 5 발견(오리가 또렷함, 눌러서 보내기) / 6 완료(3초) -- 도감·설정 아이콘 있음
 *                 보낼 만남이 없으면 메인 화면(MainScreen)
 *
 * 공통 배치: 머리글(앱 이름) 아래 영역의 가운데에 내용을 놓는다. 오리는 420dp 칸 안에 292dp로 그리고,
 * 제목은 36 굵게(줄 높이 52), 부제는 28이다. 원(배경)은 없고 오리만 보인다.
 *
 * 움직임: 정차 중인 5번의 통통 튐과 컨페티, 주행 중 3번의 "찾는 중" 와이파이 모션이 있다.
 * 가이드는 주행 중 시선을 끄는 애니메이션을 금지하므로, 주행 중 모션은 상태를 알리는 느린 로딩 표시 하나뿐이고
 * 설정값(DuckConfig.SEARCH_ANIMATION_WHILE_DRIVING)으로 끌 수 있다.
 */

private val DuckSlot = 420.dp
private val DuckImageSize = 292.dp

/**
 * 머리글(앱 이름)과 가운데 정렬 영역.
 * showIcons=true(정차·주차)면 오른쪽에 도감·설정 아이콘이 있고, 주행 중 화면은 아이콘이 없다.
 */
@Composable
internal fun CenterShell(
    appName: String,
    modifier: Modifier = Modifier,
    showIcons: Boolean = false,
    onCollection: () -> Unit = {},
    onSettings: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = duckColors
    Column(modifier.fillMaxSize().background(c.basic50).padding(start = 40.dp, end = 40.dp, top = 40.dp)) {
        MainHeader(appName, showIcons, onCollection, onSettings)
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun TitleText(text: String) {
    val c = duckColors
    Text(text, style = Type.titleMedium.copy(lineHeight = 52.sp), color = c.textPrimary, textAlign = TextAlign.Center)
}

@Composable
private fun SubText(text: String) {
    val c = duckColors
    Text(text, style = Type.bodyMedium.copy(lineHeight = 40.sp), color = c.textSecondary, textAlign = TextAlign.Center)
}

// ---------------- 주행 ----------------

/** 주행 중: 카드 상태에 따라 3(찾는 중) / 4(발견) / 받았어요 알림 */
@Composable
fun DrivingRoot(
    state: DuckDuckState, modifier: Modifier = Modifier, binHolder: DuckBinHolder? = null,
    controller: DuckDuckController? = null, onCollection: () -> Unit = {}, onSettings: () -> Unit = {},
) {
    // 오리 통 화면: 주행 중에도 통이 보이고, 오리를 받으면 왼쪽에서 들어온다(발견·도착 안내 화면은 쓰지 않는다)
    if (DuckConfig.BIN_MAIN_SCREEN && binHolder != null && controller != null) {
        BinMainScreen(state, binHolder, controller, onCollection, onSettings, modifier)
        return
    }
    when (state.card) {
        is Card.Found -> DiscoverScreen(state, enabled = false, controller = null, modifier = modifier)
        // 오리를 받았을 때(주행 중): 종류는 숨기고 멈추면 확인할 수 있다고만 알린다
        is Card.Arrived -> CenterShell(state.appName, modifier) { TitleText("오리를 받았어요!\n주정차시 확인할 수 있어요") }
        else -> SearchingScreen(state, modifier)
    }
}

/** 3번: 주행 중... 근처의 ○○를 찾고 있어요. 원이 와이파이가 켜지듯 안쪽부터 차례로 켜져서 "찾는 중"임을 알린다 */
@Composable
private fun SearchingScreen(state: DuckDuckState, modifier: Modifier) {
    CenterShell(state.appName, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SearchingMark()
            Spacer(Modifier.height(23.dp))
            TitleText("주행 중...")
            Spacer(Modifier.height(19.dp))
            SubText("근처의 ${Josa.obj(state.modelName)} 찾고 있어요")
        }
    }
}

/**
 * 겹친 세 개의 원(바깥 129, 가운데 92, 안쪽 56). 안쪽 점은 늘 켜져 있고,
 * 가운데 원 -> 바깥 원 순서로 차례로 켜졌다가 함께 꺼지기를 반복한다 (약 2.2초, 와이파이가 켜지는 모양).
 * 눈에 거슬리지 않게 느리고 깜빡임(점멸)이 없다.
 */
@Composable
private fun SearchingMark() {
    val yellow = duckColors.regulationYellow
    val progress = if (DuckConfig.SEARCH_ANIMATION_WHILE_DRIVING) {
        rememberInfiniteTransition(label = "search").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
            label = "search",
        )
    } else null

    /** p가 a에서 b로 가는 동안 0 -> 1 */
    fun ramp(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)

    Canvas(Modifier.size(130.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val p = progress?.value ?: 1f
        val fadeOut = 1f - ramp(p, 0.86f, 1f)                       // 마지막에 함께 꺼진다
        val mid = if (progress == null) 1f else ramp(p, 0.10f, 0.26f) * fadeOut
        val outer = if (progress == null) 1f else ramp(p, 0.38f, 0.54f) * fadeOut
        // 켜질 때 살짝 커지면서 나타난다
        drawCircle(yellow.copy(alpha = 0.22f * outer), radius = 65.dp.toPx() * (0.86f + 0.14f * outer), center = center)
        drawCircle(yellow.copy(alpha = 0.38f * mid), radius = 46.dp.toPx() * (0.86f + 0.14f * mid), center = center)
        drawCircle(yellow, radius = 28.dp.toPx(), center = center)
    }
}

// ---------------- 정차 ----------------

/** 6번: 오리가 출발했어요 (노란 체크). 3초 뒤 자동으로 넘어간다 */
@Composable
fun SentScreen(state: DuckDuckState, onCollection: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    CenterShell(state.appName, modifier, showIcons = true, onCollection = onCollection, onSettings = onSettings) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CheckCircle()
            Spacer(Modifier.height(60.dp))
            TitleText("오리가 출발했어요")
        }
    }
}

/** 노란 체크 원. Phosphor check-circle은 원 지름이 아이콘 크기의 약 81%라서 128dp로 그리면 원이 104dp(와이어프레임)가 된다 */
@Composable
private fun CheckCircle() {
    PhIcon(R.drawable.ph_check_circle, duckColors.regulationYellow, size = 128.dp)
}

/**
 * 받은 오리 확인 결과 (정차·주차). 큰 오리(520dp) 아래에 흰 카드(600×172): 오리별 메시지(36 굵게)와 보낸 차 닉네임(24).
 * 받은 오리는 정차·주차가 되는 순간 자동으로 이 화면에 뜨고 3초 뒤 자동으로 넘어간다.
 * (와이어프레임에 없는 "새 오리 발견" 표시는 넣지 않았다 [팀 확인])
 */
@Composable
fun ResultScreen(card: Card.Result, appName: String, onCollection: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = duckColors
    val type = card.entry.type
    CenterShell(appName, modifier, showIcons = true, onCollection = onCollection, onSettings = onSettings) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 낮은 창에서는 카드와 간격 자리를 빼고 오리를 줄인다
            val imageSize = minOf(520.dp, (maxHeight - 25.dp - 172.dp).coerceAtLeast(160.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DuckTypeImage(type, imageSize)
                Spacer(Modifier.height(25.dp))
                Column(
                    Modifier.width(600.dp).height(172.dp).clip(RoundedCornerShape(24.dp)).background(c.basic00).padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        type.message, style = Type.titleMedium.copy(lineHeight = 52.sp), color = c.textPrimary,
                        textAlign = TextAlign.Center, maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        card.entry.fromNickname, style = Type.labelMedium.copy(lineHeight = 34.sp), color = c.textSecondary,
                        textAlign = TextAlign.Center, maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------- 발견 (4: 주행, 5: 정차) ----------------

/**
 * 4번(enabled=false, 주행 중): 오리가 흐릿하고 눌러도 아무 일이 없다. 움직임 없음.
 * 5번(enabled=true, 정차): 오리가 또렷하고, 눌러야 한다는 것을 알리려고 통통 튄다. 처음 나타날 때 컨페티가 한 번 터진다.
 */
@Composable
fun DiscoverScreen(
    state: DuckDuckState,
    enabled: Boolean,
    controller: DuckDuckController?,
    onCollection: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var notice by remember { mutableStateOf<String?>(null) }
    val type = state.previewDuck ?: DuckType.BASIC
    Box(modifier.fillMaxSize()) {
        CenterShell(state.appName, Modifier, showIcons = enabled, onCollection = onCollection, onSettings = onSettings) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DuckButton(type, enabled) {
                    notice = when (controller?.sendSuggested()) {
                        is SendOutcome.Sent, null -> null
                        SendOutcome.DeliveryFailed -> "오리를 보내지 못했어요. 상대가 앱을 껐을 수 있어요"
                        SendOutcome.NoNickname -> "주차해서 닉네임을 먼저 정해 주세요"
                        SendOutcome.NotAllowedWhileMoving -> "멈춘 뒤에 보낼 수 있어요"
                        is SendOutcome.Rejected, SendOutcome.NoNearbyCar, SendOutcome.NotInBin -> "지금은 보낼 수 없어요"
                    }
                }
                Spacer(Modifier.height(48.dp))
                TitleText("${Josa.obj(state.modelName)} 발견했어요!\n오리를 눌러 보내보세요")
                Spacer(Modifier.height(19.dp))
                SubText(notice ?: "주정차시 보낼 수 있어요")
            }
        }
        // 정차 중 새 만남이 뜰 때마다 한 번 터뜨린다 (티켓이 바뀌면 다시)
        if (enabled) key(state.suggested?.ticket?.id) { ConfettiBurst(originYFraction = 542f / 1210f) }
    }
}

/**
 * 오리 버튼(원 없이 오리만). 420dp 칸 안에 292dp 오리를 둔다.
 * enabled일 때만 눌리고(누르면 95%로 줄어듦), "눌러 보세요" 움직임으로 위아래로 통통 튄다. 튈 때 바닥 그림자는 작아진다.
 */
@Composable
private fun DuckButton(type: DuckType, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hopState = if (enabled) {
        rememberInfiniteTransition(label = "hop").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 1600
                    0f at 0
                    1f at 260 using FastOutSlowInEasing   // 위로 튀어 오름
                    0f at 520 using FastOutSlowInEasing   // 내려앉음
                    0f at 1600                            // 잠깐 쉼
                },
            ),
            label = "hop",
        )
    } else null

    Box(
        Modifier.size(DuckSlot)
            .then(if (enabled) Modifier.pressScale(source).clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (hopState != null) {
            // 바닥 그림자: 오리가 튀어 오르면 작고 옅어진다
            Canvas(Modifier.fillMaxSize()) {
                val hop = hopState.value
                val w = 190.dp.toPx() * (1f - 0.25f * hop)
                val h = 26.dp.toPx() * (1f - 0.25f * hop)
                drawOval(
                    Color.Black.copy(alpha = 0.10f * (1f - 0.5f * hop)),
                    topLeft = Offset(size.width / 2 - w / 2, size.height * 0.83f - h / 2),
                    size = Size(w, h),
                )
            }
        }
        Box(
            Modifier.size(DuckImageSize)
                .then(if (enabled) Modifier else Modifier.alpha(0.4f))   // 주행 중에는 흐릿하게
                .graphicsLayer {
                    val hop = hopState?.value ?: 0f
                    translationY = -hop * 18.dp.toPx()
                    val s = 1f + 0.05f * hop
                    scaleX = s; scaleY = s
                },
        ) { DuckTypeImage(type, DuckImageSize) }
    }
}

// ---------------- 컨페티 ----------------

private class Piece(val vx: Float, val vy: Float, val size: Float, val color: Color, val rot0: Float, val rotSpeed: Float)

private const val CONFETTI_MS = 1700
private const val GRAVITY = 1500f   // dp/s^2

/**
 * 간단한 컨페티: 오리 자리에서 사방으로 터져 나가 떨어지며 사라진다(약 1.7초, 한 번). 정차 중에만 쓴다.
 * 조각은 같은 모양이 매번 나오도록 고정된 난수로 만든다.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, originYFraction: Float = 0.45f) {
    val palette = listOf(
        Color(0xFFFFC224), Color(0xFFFF8A00), Color(0xFF0082FF), Color(0xFF00BA13), Color(0xFFF62E24), Color(0xFF5A46FA),
    )
    val pieces = remember {
        val r = kotlin.random.Random(7)
        List(56) {
            Piece(
                vx = r.nextFloat() * 1300f - 650f,
                vy = -(350f + r.nextFloat() * 750f),
                size = 10f + r.nextFloat() * 8f,
                color = palette[r.nextInt(palette.size)],
                rot0 = r.nextFloat() * 360f,
                rotSpeed = r.nextFloat() * 720f - 360f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val sec = progress.value * CONFETTI_MS / 1000f
        val alpha = (1f - (progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)   // 끝 40% 동안 사라진다
        val d = density
        val ox = size.width / 2
        val oy = size.height * originYFraction
        pieces.forEach { p ->
            val x = ox + p.vx * d * sec
            val y = oy + (p.vy * sec + 0.5f * GRAVITY * sec * sec) * d
            rotate(p.rot0 + p.rotSpeed * sec, pivot = Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.size * d / 2, y - p.size * d / 4),
                    size = Size(p.size * d, p.size * d / 2),
                )
            }
        }
    }
}
