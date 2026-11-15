package com.example.chargesuggest.ui.duckduck

import android.content.Context
import com.example.chargesuggest.duckduck.DuckDuckController
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Josa
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.physics.DriveMotion
import com.example.chargesuggest.duckduck.physics.DuckBody
import com.example.chargesuggest.duckduck.physics.DuckShape
import com.example.chargesuggest.duckduck.physics.DuckWorld
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.duckColors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/*
 * 오리 통 메인 화면. 차 안의 통에 오리가 가득 담겨 있고, 화면 전체가 통이다.
 *
 *   배경     제목("Duck, Duck, Beep!")과 오리 수("15 / 15")가 뒤에 있고, 오리가 그 위에 쌓인다(시안처럼 글자를 가려도 된다)
 *   오리     DuckWorld(물리)가 계산한 위치에 오리 이미지를 그린다. 새로 들어온 오리는 위에서 떨어진다
 *   터치     손가락으로(주행 중에도 DuckConfig.DRIVING_DUCK_TOUCH가 true면) 오리를 집어 끌고 던지면 화면 안에서 통통 튄다(보내기는 이후 단계)
 *   차 반응  주행에서 정차로 바뀌는 순간 오리들이 앞쪽(왼쪽)으로 쏠렸다가 가라앉는다
 *   위쪽     도감·설정 아이콘 (이동은 정차·주차에서만)
 *
 * 움직임이 없으면 계산도 프레임도 멈춘다(CPU를 쓰지 않는다). 터치·새 오리·차의 움직임이 오면 다시 깨어난다.
 */

/** 화면이 바뀌어도 오리 위치가 유지되게, 서비스 화면이 들고 있는 오리 통 상태 */
class DuckBinHolder(val motion: DriveMotion = DriveMotion()) {
    var world: DuckWorld? = null
        private set
    private var w = 0f
    private var h = 0f

    /** 물리를 돌리는 쪽을 깨우는 신호 */
    val wake = Channel<Unit>(Channel.CONFLATED)

    /** 시연용: 통 맨 위의 오리(받은 오리 표시가 없는 것)를 위로 날려 보낸다. 날려 보냈으면 true */
    fun throwTopDuck(freshIds: Set<Int>): Boolean {
        val launched = world?.launchTopDuck(freshIds) ?: return false
        poke()
        return launched != null
    }

    /** 통을 처음 상태로 비운다. 다음에 메인 화면이 뜰 때 오리들이 위에서 다시 쏟아져 내려온다(처음 시작, "처음부터") */
    fun reset() { world = null; w = 0f; h = 0f }

    /** 화면 크기가 바뀌면 새로 만든다(오리가 다시 쏟아진다) */
    fun ensure(width: Float, height: Float, capacity: Int): DuckWorld {
        val cur = world
        if (cur != null && w == width && h == height) return cur
        w = width; h = height
        return DuckWorld(width, height, DuckWorld.duckSizeFor(width, height, capacity)).also { world = it }
    }

    fun poke() { wake.trySend(Unit) }

    /** 출발·정차 때의 쏠림. 오리 크기에 비례한 속도라 화면 크기와 상관없이 비슷하게 보인다 */
    fun onModeChanged(from: DriveMode, to: DriveMode) {
        val wd = world ?: return
        val s = wd.size
        when {
            from == DriveMode.DRIVING && to != DriveMode.DRIVING -> wd.nudge(-5f * s)  // 멈추면 앞으로 확 쏠림
            from != DriveMode.DRIVING && to == DriveMode.DRIVING -> return                // 출발하면 앞쪽으로 기울어진다(아래 LaunchedEffect)
            else -> return
        }
        poke()
    }
}

@Composable
internal fun BinMainScreen(
    state: DuckDuckState,
    holder: DuckBinHolder,
    controller: DuckDuckController,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = duckColors
    val driving = state.mode == DriveMode.DRIVING
    val touchEnabled = !driving || DuckConfig.DRIVING_DUCK_TOUCH

    Box(modifier.fillMaxSize().background(c.basic00)) {
        // 머리글(앱 이름, 도감·설정 아이콘)은 맨 아래에 깔고 오리가 그 위로 쌓인다. 주행 중에도 아이콘이 보인다
        Box(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp).height(64.dp), contentAlignment = Alignment.CenterStart) {
            Text(BIN_TITLE, style = Type.titleMedium, color = c.textPrimary)
        }
        Box(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp)) {
            MainHeader("", showIcons = true, onCollection = {}, onSettings = {})
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            DuckPile(state, holder, controller, widthPx, heightPx, touchEnabled, driving, onCollection, onSettings)
        }

        // 던진 오리 "보낼까요?" / "오리를 보냈어요!" / 받은 오리 확인 카드
        BinCards(state, controller)

        // 처음 시작 튜토리얼
        TutorialOverlay(state, controller, onCollection)
    }
}

/** 앱 이름(머리글) */
private const val BIN_TITLE = DuckConfig.APP_NAME

@Composable
private fun DuckPile(
    state: DuckDuckState, holder: DuckBinHolder, controller: DuckDuckController, widthPx: Float, heightPx: Float,
    touchEnabled: Boolean, driving: Boolean, onCollection: () -> Unit, onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val onCollectionNow by rememberUpdatedState(onCollection)
    val onSettingsNow by rememberUpdatedState(onSettings)
    // 도감·설정 아이콘 자리(머리글의 64dp 칸 둘). 오리가 아이콘 위로 오기 때문에 오리가 없는 곳을 눌렀을 때만 아이콘으로 본다
    val iconTop = with(density) { 40.dp.toPx() }
    val iconSlot = with(density) { 64.dp.toPx() }
    val iconRight = widthPx - with(density) { 40.dp.toPx() }
    val iconGap = with(density) { HEADER_ICON_GAP.toPx() }
    val titleRight = with(density) { 320.dp.toPx() } // 앱 이름(로고)이 있는 왼쪽 위 칸
    fun iconAt(x: Float, y: Float): Int = when {
        y < iconTop || y > iconTop + iconSlot -> 0
        x in (iconRight - iconSlot)..iconRight -> 2 // 설정
        x in (iconRight - 2 * iconSlot - iconGap)..(iconRight - iconSlot - iconGap) -> 1 // 도감
        else -> 0
    }

    val capacity = state.bin.capacity
    val world = remember(widthPx, heightPx, capacity) { holder.ensure(widthPx, heightPx, capacity) }
    val sprites = rememberDuckSprites()
    // "보낼까요?" 확인 중인 오리는 화면 밖에 둔다(돌아가기를 누르면 다시 위에서 떨어진다)
    val pendingId = state.pendingSend?.binDuckId
    val ids = state.bin.ducks.filter { it.id != pendingId }.map { it.id }
    val duckById = remember(state.bin) { state.bin.ducks.associateBy { it.id } }
    // 밀려나는 오리의 종류를 기억해 둔다(통에서는 이미 빠졌지만 사라지는 동안 같은 모습으로 그린다)
    val typeMemory = remember { HashMap<Int, DuckType>() }
    for (d in state.bin.ducks) typeMemory[d.id] = d.type
    val freshIds = remember(state.bin) { state.bin.ducks.filter { it.fresh }.map { it.id }.toSet() }
    var frame by remember { mutableIntStateOf(0) }
    val touch by rememberUpdatedState(touchEnabled)
    val freshNow by rememberUpdatedState(freshIds)
    world.priorityIds = freshIds
    // 튜토리얼 1단계에서는 오리를 가볍게 튕겨도 던진 것으로 본다(던지기가 너무 힘들지 않게)
    val tutorialStep1Now by rememberUpdatedState(state.tutorialStep == 1 && tutorialVisible(state))
    // 받은 오리는 왼쪽에서 굴러 들어오고, 날씨 선물 오리는 위에서 떨어진다
    val fromLeftIds = remember(state.bin) { state.bin.ducks.filter { it.fresh && !it.gift }.map { it.id }.toSet() }
    val drivingNow by rememberUpdatedState(driving)
    val controllerNow by rememberUpdatedState(controller)

    // 받은 오리 표시: 오리 자체가 반짝이고(빛이 훑고 지나감) 주위에 은은한 아우라가 숨 쉬듯 퍼진다. 받은 오리가 있을 때만 움직인다
    val shimmer = if (freshIds.isNotEmpty()) {
        rememberInfiniteTransition(label = "fresh").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(keyframes { durationMillis = 2800; 0f at 0 using LinearEasing; 1f at 1200 using LinearEasing; 1f at 2800 }),
            label = "shimmer",
        )
    } else null
    val pulse = if (freshIds.isNotEmpty()) {
        rememberInfiniteTransition(label = "aura").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    } else null

    // 오리를 누를 때의 "꽥" 효과음(설정의 알림 효과음과는 별개로 항상 난다)
    val context = LocalContext.current
    val squeaker = remember { DuckSqueaker(context) }
    DisposableEffect(squeaker) { onDispose { squeaker.release() } }
    val soundOn by rememberUpdatedState(true)

    // 통의 오리 목록이 바뀌면 물리 세계에 맞춘다 (새 오리는 위에서 떨어지고, 받은 오리는 왼쪽에서 굴러 들어온다)
    LaunchedEffect(world, ids) {
        world.sync(ids, fromLeft = fromLeftIds)
        holder.poke()
    }

    // 주행 중: 오리들이 차 안의 상자에 든 것처럼 출발·회전·브레이크·요철에 따라 쏠리고 튄다.
    // 정차·주차에서는 가속도가 0이 되어 가라앉는다. DuckConfig.DRIVE_MOTION_WHILE_DRIVING=false면 주행 중에도 가만히 둔다.
    LaunchedEffect(world, driving) {
        if (!driving || !DuckConfig.DRIVE_MOTION_WHILE_DRIVING) {
            world.setAcceleration(0f)
            if (!world.asleep) holder.poke()
            return@LaunchedEffect
        }
        val motion = holder.motion
        motion.reset()
        motion.trigger(DriveMotion.Event.ACCELERATE) // 출발하면 뒤로 쏠린다
        var last = System.nanoTime()
        while (isActive) {
            delay(33)
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            val out = motion.update(dt)
            world.setAcceleration(out.ax * world.size)
            if (out.bump > 0f) world.jolt(out.bump * world.size)
            if (!world.asleep) holder.poke() // 움직일 때만 화면을 갱신한다
        }
    }

    // 프레임 루프: 움직임이 있을 때만 돈다
    LaunchedEffect(world) {
        while (isActive) {
            if (world.asleep) holder.wake.receive()
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (isActive && !world.asleep) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                world.step((now - last) / 1_000_000_000f)
                last = now
                // 위로 던져져 화면 밖으로 나간 오리: 근처에 보낼 수 있는 같은 차가 있으면 "보낼까요?"가 뜨고(오리는 화면에서 치운다),
                // 없으면 아무 일도 없이 그대로 떨어져 돌아온다
                for (id in world.pollThrownOut()) {
                    if (controllerNow.beginThrow(id)) world.removeDuck(id)
                }
                frame++
            }
            frame++
        }
    }

    Canvas(
        Modifier.fillMaxSize().pointerInput(world) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // 머리글의 도감·설정 아이콘과 앱 이름은 오리가 그 위에 쌓여 있어도 먼저 눌린다(오리에 가려 설정이 안 열리는 일이 없게)
                val which = iconAt(down.position.x, down.position.y)
                val inTitle = which == 0 && down.position.y in iconTop..(iconTop + iconSlot) && down.position.x < titleRight
                if (which != 0 || inTitle || !(touch && world.grab(down.position.x, down.position.y))) {
                    // 도감·설정 아이콘을 눌렀으면 그 화면으로 간다(손가락을 떼는 곳도 아이콘 위여야 한다)
                    if (which != 0) {
                        down.consume()
                        var last = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            last = ch.position
                            ch.consume()
                            if (!ch.pressed) {
                                if (iconAt(last.x, last.y) == which) { if (which == 1) onCollectionNow() else onSettingsNow() }
                                break
                            }
                        }
                    }
                    // 앱 이름(로고)을 누르면 오리들이 가볍게 튕긴다
                    if (inTitle) {
                        down.consume()
                        world.jolt(2.2f * world.size)
                        holder.poke()
                        if (soundOn) squeaker.play(DuckConfig.SOUND_VOLUME)
                    }
                    return@awaitEachGesture
                }
                val grabbedId = world.grabbedId
                holder.poke()
                if (soundOn) squeaker.play(DuckConfig.SOUND_VOLUME)
                down.consume()
                // 손가락 속도: 최근 0.15초의 움직임으로 직접 계산한다(점이 몇 개 안 되는 빠른 던지기에서도 값이 나오게)
                val times = ArrayList<Long>(); val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
                fun sample(t: Long, p: Offset) { times += t; xs += p.x; ys += p.y }
                sample(down.uptimeMillis, down.position)
                // 손가락을 따라가며 위치를 기록한다. 손가락을 뗄 때의 마지막 위치도 기록해야 빠른 던지기의 속도가 나온다
                var ok = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    sample(ch.uptimeMillis, ch.position)
                    if (!ch.pressed) { ok = true; ch.consume(); break }
                    world.dragTo(ch.position.x, ch.position.y)
                    holder.poke()
                    ch.consume()
                }
                var vx = 0f; var vy = 0f
                // 마지막 움직임 뒤로 0.1초 넘게 멈춰 있다가 뗀 것은 던진 것이 아니다
                val stillMs = android.os.SystemClock.uptimeMillis() - (times.lastOrNull() ?: 0L)
                if (ok && times.size >= 2 && stillMs <= 100L) {
                    val last = times.size - 1
                    var first = last
                    while (first > 0 && times[last] - times[first - 1] <= 150L) first--
                    val dt = (times[last] - times[first]) / 1000f
                    if (first < last && dt >= 0.008f) {
                        vx = (xs[last] - xs[first]) / dt
                        vy = (ys[last] - ys[first]) / dt
                    }
                }
                // 튜토리얼 1단계는 느리게 끌어 올렸거나 놓기 전에 잠깐 멈춰도 던진 것으로 본다(마우스·시연 때 오리가 안 날아가는 일이 없게)
                if (tutorialStep1Now && ok && grabbedId != null && ys.first() - ys.last() > world.size * 0.8f && vy > -world.size * 1.5f) {
                    vx = 0f; vy = -world.size * 8f
                }
                // 놓는 순간의 손가락 속도로 던진다(손가락이 멈춰 있었으면 그냥 놓인다)
                world.instantThrowSizes = if (tutorialStep1Now) 1.2f else 4f
                world.release(vx, vy)
                holder.poke()
                // 거의 움직이지 않고 짧게 눌렀다 뗐으면 "누르기": 받은 오리 표시가 있는 오리면 확인한다(주행 중에는 종류를 볼 수 없다)
                val moved = kotlin.math.hypot(xs.last() - xs.first(), ys.last() - ys.first())
                if (ok && grabbedId != null && grabbedId in freshNow && (!drivingNow || DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING) && moved < 28f && times.last() - times.first() < 350L) {
                    controllerNow.openBinDuck(grabbedId)
                }
            }
        },
    ) {
        frame // 프레임이 바뀔 때마다 다시 그린다
        val size = world.size
        val sweep = shimmer?.value ?: 1f
        val breathe = pulse?.value ?: 0f

        fun drawOneDuck(b: DuckBody, type: DuckType, fresh: Boolean, scale: Float = 1f, alpha: Float = 1f) {
            val sp = sprites.forType(type)
            // 오리마다 그림의 가로세로 비율이 달라서(모자·우산) 기본 오리가 차지하는 칸 안에 비율을 지켜 가운데로 맞춰 넣는다
            val boxW = size
            val boxH = size * DuckShape.HEIGHT_RATIO
            // 모자·우산이 있는 오리는 몸통이 기본 오리보다 작아 보이므로 조금 키운다(충돌 모양은 기본 오리 기준이라 장식은 살짝 겹칠 수 있다)
            val fit = minOf(boxW / sp.cropW, boxH / sp.cropH) * costumeScale(type)
            val dstW = (sp.cropW * fit).toInt()
            val dstH = (sp.cropH * fit).toInt()
            // 무게중심(DuckShape.comX/Y)이 그림의 어디인지에 맞춰, 칸의 왼쪽 위를 무게중심 기준으로 놓는다
            val offX = (-DuckShape.comX * size + (boxW - dstW) / 2f).toInt()
            val offY = (-DuckShape.comY * size + (boxH - dstH) / 2f).toInt()
            if (fresh) {
                // 아우라: 오리 뒤로 따뜻한 주황빛이 숨 쉬듯 퍼진다
                val r = size * (0.62f + 0.05f * breathe)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to AuraColor.copy(alpha = 0.22f + 0.10f * breathe), 0.6f to AuraColor.copy(alpha = 0.08f), 1f to Color.Transparent,
                        center = Offset(b.x, b.y), radius = r,
                    ),
                    radius = r, center = Offset(b.x, b.y),
                )
            }
            translate(b.x, b.y) {
              scale(scale, scale, Offset.Zero) {
                rotate(Math.toDegrees(b.angle.toDouble()).toFloat(), Offset.Zero) {
                    if (fresh) drawContext.canvas.saveLayer(Rect(offX.toFloat(), offY.toFloat(), (offX + dstW).toFloat(), (offY + dstH).toFloat()), Paint())
                    drawImage(
                        sp.image,
                        srcOffset = IntOffset(sp.cropX, sp.cropY), srcSize = IntSize(sp.cropW, sp.cropH),
                        dstOffset = IntOffset(offX, offY), dstSize = IntSize(dstW, dstH),
                        alpha = alpha,
                        filterQuality = FilterQuality.Medium,
                    )
                    if (fresh) {
                        // 반짝임: 오리 모양 안쪽에만 비스듬한 빛 띠가 왼쪽에서 오른쪽으로 훑고 지나간다(스켈레톤 UI의 반짝임처럼)
                        val band = dstW * 0.5f
                        val x0 = offX - band + (dstW + band * 2f) * sweep
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.9f), Color.Transparent),
                                start = Offset(x0, offY.toFloat()), end = Offset(x0 + band, offY + band * 0.7f),
                            ),
                            topLeft = Offset(offX.toFloat(), offY.toFloat()), size = Size(dstW.toFloat(), dstH.toFloat()),
                            blendMode = BlendMode.SrcAtop,
                        )
                        drawContext.canvas.restore()
                    }
                }
              }
            }
        }

        // 오리 그림자: 모든 오리 뒤에 먼저 깔아서 배경(흰 바닥)에 부드럽게 비친다. 빛이 왼쪽 위에서 오므로 오른쪽 아래로 퍼진다
        if (DuckConfig.DUCK_SHADOW) {
            val dx = size * 0.07f
            val dy = size * 0.10f
            for (b in world.bodies) {
                for (k in 0 until 4) {
                    val (cx, cy, r) = world.circleOf(b, k)
                    val sr = r * 1.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to ShadowColor.copy(alpha = 0.16f), 0.55f to ShadowColor.copy(alpha = 0.09f), 1f to Color.Transparent,
                            center = Offset(cx + dx, cy + dy), radius = sr,
                        ),
                        radius = sr, center = Offset(cx + dx, cy + dy),
                    )
                }
            }
        }

        // 받은 오리는 맨 위에 그려서 아우라와 반짝임이 가려지지 않게 한다
        for (b in world.bodies) { if (b.id !in freshIds) drawOneDuck(b, duckById[b.id]?.type ?: typeMemory[b.id] ?: DuckType.BASIC, false) }
        for (b in world.bodies) { if (b.id in freshIds) drawOneDuck(b, duckById[b.id]?.type ?: typeMemory[b.id] ?: DuckType.BASIC, true) }
    }
}


/** 오리 그림자 색(카드 그림자와 같은 #091432). 투명도는 그릴 때 정한다 */
private val ShadowColor = Color(0xFF091432)

/** 장식(모자·우산)이 있는 오리의 그림 확대 비율 */
private fun costumeScale(type: DuckType): Float = when (type) {
    DuckType.SNOWMAN -> 1.2f
    DuckType.UMBRELLA -> 1.25f
    else -> 1f
}

/** 받은 오리의 아우라 색(노란빛) */
private val AuraColor = Color(0xFFFFC224)

// ---------------- 오리 이미지 ----------------

/** 오리 그림과, 투명 여백을 뺀 오리 영역(crop) */
private class Sprite(val image: ImageBitmap, val cropX: Int, val cropY: Int, val cropW: Int, val cropH: Int)

private class DuckSprites(private val byType: Map<DuckType, Sprite>, private val fallback: Sprite) {
    fun forType(type: DuckType): Sprite = byType[type] ?: fallback
}

/**
 * res/drawable 의 `duck_<오리 id>` 이미지를 불러와 투명 여백을 잰다.
 * 아직 이미지가 없는 종류는 기본 오리 이미지로 대신 그린다.
 */
@Composable
private fun rememberDuckSprites(): DuckSprites {
    val context = LocalContext.current
    return remember(context) {
        val loaded = mutableMapOf<DuckType, Sprite>()
        for (t in DuckType.values()) {
            val id = context.resources.getIdentifier("duck_${t.id}", "drawable", context.packageName)
            if (id != 0) loaded[t] = loadSprite(context, id)
        }
        DuckSprites(loaded, loaded[DuckType.BASIC] ?: error("res/drawable/duck_basic 이미지가 없습니다"))
    }
}

private fun loadSprite(context: Context, resId: Int): Sprite {
    val image = ImageBitmap.imageResource(context.resources, resId)
    val bmp = image.asAndroidBitmap()
    val w = bmp.width; val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 20) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return Sprite(image, 0, 0, w, h)
    return Sprite(image, minX, minY, maxX - minX + 1, maxY - minY + 1)
}
