package com.example.chargesuggest.ui.duckduck

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import com.example.chargesuggest.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duckduck.Card
import com.example.chargesuggest.duckduck.DuckDuckController
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.SendOutcome
import com.example.chargesuggest.duckduck.domain.AdjectiveGroup
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Josa
import com.example.chargesuggest.duckduck.domain.NicknameAdjectives
import com.example.chargesuggest.duckduck.domain.StopPrompt
import com.example.chargesuggest.duckduck.domain.TicketStatus
import com.example.chargesuggest.duckduck.domain.TicketView
import com.example.chargesuggest.ui.duck.BasicButton
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.SegmentedTabs
import com.example.chargesuggest.ui.duck.StatusChip
import com.example.chargesuggest.ui.duck.duckColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 명세(duck-app-spec.md 6장)의 화면을 상태별로 그린다. 와이어프레임을 받으면 배치만 바꾸면 되도록
 * 화면 하나하나를 별도 함수로 나눴다. 색·글자는 Pleos 디자인 시스템 토큰(ui/duck/DuckTheme.kt)을 쓴다.
 *
 *   주행(MOVING): S2 찾는 중 / S3 발견 / R1 도착 / R2·S6 배지      -- 터치 조작 없음
 *   정차(IDLING): S4 오리 보내기 / S5 완료 / R3 오리 확인하기 / R4 결과 -- 버튼 하나
 *   주차(PARKED): S0 온보딩 / S1 메인 / R6 도감 / SET 설정
 */

/**
 * 차 한 대의 서비스 화면. 주행 상태에 따라 보여 줄 화면이 바뀐다 (와이어프레임 기준).
 *  - 주행: 3 찾는 중 / 4 발견 / 받았어요 알림                -> DrivingRoot (DuckDuckDriving.kt)
 *  - 정차: 받은 오리 확인(자동) / 5 발견(눌러서 보내기) / 6 완료 / 메인
 *  - 주차: 받은 오리 확인(자동) / 메인
 *  - 정차·주차: 도감·설정 아이콘으로 도감·설정 화면을 열 수 있다 (주행 중에는 아이콘이 없다)
 * 온보딩이 끝나지 않았으면 주차에서만 온보딩을 보여 주고, 그 밖에는 "정차 후 시작할 수 있어요" 한 줄만 보인다.
 */
@Composable
fun DuckDuckService(
    state: DuckDuckState, controller: DuckDuckController, wide: Boolean, modifier: Modifier = Modifier,
    binHolder: DuckBinHolder = remember { DuckBinHolder() },
) {
    val c = duckColors
    var screen by remember { mutableStateOf(ParkedScreen.MAIN) }
    // 오리 통은 화면이 바뀌어도 오리 위치가 유지되도록 여기서 들고 있는다
    var lastMode by remember { mutableStateOf(state.mode) }
    // 닉네임을 정하기 전(처음 시작·"처음부터")에는 통을 비워 둔다: 메인에 들어올 때 오리들이 위에서 쏟아져 내려온다
    LaunchedEffect(state.onboarded) { if (!state.onboarded) binHolder.reset() }
    LaunchedEffect(state.mode) {
        binHolder.onModeChanged(lastMode, state.mode)
        lastMode = state.mode
    }
    // (이전 화면 구성) 출발하면 도감·설정을 닫고 주행 화면으로 돌아간다. 오리 통 화면에서는 주행 중에도 도감·설정을 볼 수 있다
    LaunchedEffect(state.mode) { if (!DuckConfig.BIN_MAIN_SCREEN && state.mode == DriveMode.DRIVING) screen = ParkedScreen.MAIN }
    val onCollection = { screen = ParkedScreen.COLLECTION; controller.onCollectionOpened() }
    val onSettings = { screen = ParkedScreen.SETTINGS }
    val back = { screen = ParkedScreen.MAIN }

    if (!state.onboarded) {
        if (state.mode == DriveMode.PARKED || DuckConfig.ONBOARD_WHILE_DRIVING) {
            // 닉네임만 정하고 바로 메인으로 들어간다(온보딩 없음). 메인에서 튜토리얼이 기능을 하나씩 알려 준다
            NicknameStart(state, controller, modifier)
        } else {
            CenterShell(state.appName, modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(19.dp)) {
                    Text("정차 후 시작할 수 있어요", style = Type.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
                    Text("주차(P)에서 처음 닉네임을 정해 주세요", style = Type.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }

    val card = state.card
    // 보여 줄 화면: 도감·설정(오리 통 화면에서는 주행 중에도 열 수 있다. 주행 중 설정 값 변경은 컨트롤러가 막는다) 또는 메인
    val allowSub = DuckConfig.BIN_MAIN_SCREEN || state.mode != DriveMode.DRIVING
    val shown = when {
        screen == ParkedScreen.COLLECTION && allowSub -> ParkedScreen.COLLECTION
        screen == ParkedScreen.SETTINGS && allowSub -> ParkedScreen.SETTINGS
        else -> ParkedScreen.MAIN
    }
    // 화면 전환 모션: 도감·설정은 오른쪽에서 살짝(화면 폭의 1/14) 밀려 들어오며 나타나고, 메인으로 돌아올 때는 반대로 살짝 밀려 나간다. 과하지 않게 0.3초 안쪽
    AnimatedContent(
        targetState = shown,
        transitionSpec = {
            if (targetState != ParkedScreen.MAIN) {
                (fadeIn(tween(280, delayMillis = 40)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 14 }) togetherWith
                    fadeOut(tween(160))
            } else {
                fadeIn(tween(280, delayMillis = 40)) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 14 })
            }
        },
        label = "screen",
    ) { target ->
        when {
            target == ParkedScreen.COLLECTION -> SubScreen("내 도감", back, modifier) { CollectionTab(state) }
            target == ParkedScreen.SETTINGS -> SubScreen("설정", back, modifier) { SettingsTab(state, controller) }
            state.mode == DriveMode.DRIVING -> DrivingRoot(state, modifier, binHolder, controller, onCollection, onSettings)
            // 받은 오리 확인 결과는 어느 화면 위에서든 3초 동안 먼저 보여 준다 (끝나면 보던 화면으로 돌아간다)
            !DuckConfig.BIN_MAIN_SCREEN && card is Card.Result -> ResultScreen(card, state.appName, onCollection, onSettings, modifier)
            !DuckConfig.BIN_MAIN_SCREEN && state.mode == DriveMode.STOPPED && card is Card.Sent -> SentScreen(state, onCollection, onSettings, modifier)
            !DuckConfig.BIN_MAIN_SCREEN && state.mode == DriveMode.STOPPED && state.suggested != null ->
                DiscoverScreen(state, enabled = true, controller = controller, onCollection = onCollection, onSettings = onSettings, modifier = modifier)
            DuckConfig.BIN_MAIN_SCREEN -> BinMainScreen(state, binHolder, controller, onCollection, onSettings, modifier)
            else -> MainScreen(state, controller, wide, onCollection, onSettings, modifier.background(c.basic50))
        }
    }
}

private val md = SimpleDateFormat("M/d", Locale.KOREA)

// ---------------- 주차 ----------------

/** R6: 도감. 받은(확인한) 오리는 색과 횟수로, 못 받은 오리는 실루엣과 힌트로 보여 준다 */
@Composable
internal fun CollectionTab(state: DuckDuckState) {
    val c = duckColors
    val collected = state.book.collectedTypes()
    // 도감 순서: 기본 오리 먼저, 그다음 날씨 오리들(숨겨진 무지개 오리는 맨 끝)
    val order = listOf(DuckType.BASIC, DuckType.UMBRELLA, DuckType.RAINCOAT, DuckType.SNOWMAN, DuckType.SUNGLASSES, DuckType.SCARF, DuckType.RAINBOW)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 창 너비로 칸 수를 정한다(와이드 4칸, 중간 3칸, 좁으면 2칸)
        val columns = when { maxWidth >= 1100.dp -> 4; maxWidth >= 760.dp -> 3; else -> 2 }
        LazyVerticalGrid(
            GridCells.Fixed(columns), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(order.size) { i ->
                val t = order[i]
                // 기본 오리는 처음부터 가지고 있다. 나머지는 확인(열어 봄)한 오리만 보인다
                val has = t == DuckType.BASIC || t in collected
                Column(
                    Modifier.appear(i).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.basic50).padding(horizontal = 28.dp, vertical = 24.dp),
                ) {
                    DuckTypeImage(t, 200.dp, modifier = Modifier.align(Alignment.CenterHorizontally), silhouette = !has)
                    Spacer(Modifier.height(16.dp))
                    Text(if (t == DuckType.BASIC) "일반" else "날씨", style = Type.labelMedium, color = c.textSecondary)
                    Text(if (has) t.label else "??", style = Type.bodyLarge, color = c.textPrimary, maxLines = 1)
                }
            }
        }
    }
}

/** 못 받은 오리의 힌트. 히든 무지개 오리는 힌트를 주지 않는다 */
private fun hint(t: DuckType) = when (t) {
    DuckType.RAINBOW -> "???"
    DuckType.SNOWMAN -> "눈 오는 날"
    DuckType.RAINCOAT -> "장대비 오는 날"
    DuckType.UMBRELLA -> "비 오는 날"
    DuckType.SUNGLASSES -> "더운 날"
    DuckType.SCARF -> "추운 날"
    DuckType.BASIC -> "평범한 날"
}

/** SET: 닉네임 셔플, 오리 알림(꽥!), 알림 조명과 색 */
@Composable
internal fun SettingsTab(state: DuckDuckState, controller: DuckDuckController) {
    val c = duckColors
    var spins by remember { mutableIntStateOf(0) }
    val angle by animateFloatAsState(spins * 360f, tween(450), label = "shuffle")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 내 닉네임: 마음에 드는 이름이 나올 때까지 셔플
        SettingCard(
            "내 닉네임", "오리를 보낼 때 상대방이 볼 수 있어요", index = 0,
        ) {
            Text(state.nickname?.text ?: "-", style = Type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .clickable { if (controller.shuffleAdjective() != null) spins++ }
                    .semantics { contentDescription = "닉네임 다시 뽑기" },
                contentAlignment = Alignment.Center,
            ) { PhIcon(R.drawable.ph_arrows_clockwise, c.textPrimary, size = 44.dp, modifier = Modifier.graphicsLayer { rotationZ = angle }) }
        }
        SettingCard("오리 알림", "오리를 보내고 받을 때 꽥! 소리가 나요", index = 1) {
            PillSwitch(state.settings.soundOn) { controller.setSoundOn(it) }
        }
        SettingCard("알림 조명", "오리를 보내고 받을 때 차 안 조명이 잠깐 빛나요", index = 2) {
            PillSwitch(state.settings.ambientOn) { controller.setAmbientOn(it) }
        }
        // 알림 조명을 끄면 조명 색은 흐리게 보이고 누를 수 없다
        val colorsOn = state.settings.ambientOn
        SettingCard("조명 색", ambientName(state.settings.ambientRgb), dimmed = !colorsOn, index = 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                for (rgb in DuckConfig.AMBIENT_CHOICES) {
                    val selected = (state.settings.ambientRgb and 0xFFFFFF) == rgb
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .then(if (selected) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                            .clickable(enabled = colorsOn) { controller.setAmbientRgb(rgb) }
                            .semantics { contentDescription = "조명 색 ${"%06X".format(rgb)}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb))
                                .then(if (rgb == 0xFFFFFF) Modifier.border(2.dp, c.basic300, CircleShape) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** 조명 색 이름(설명 줄에 보인다) */
private fun ambientName(rgb: Int): String = when (rgb and 0xFFFFFF) {
    0xFFC224 -> "오리 노랑"
    0xFFFFFF -> "오리 하양"
    0x0064FF -> "오리 파랑"
    0x02C265 -> "오리 초록"
    0xFF8A00 -> "오리 주황"
    else -> "직접 고른 색"
}

/** 설정 한 줄: 왼쪽에 제목과 설명, 오른쪽에 값·스위치 */
@Composable
private fun SettingCard(title: String, sub: String?, dimmed: Boolean = false, index: Int = 0, trailing: @Composable RowScope.() -> Unit) {
    val c = duckColors
    Row(
        Modifier.appear(index).fillMaxWidth().heightIn(min = 168.dp).alpha(if (dimmed) 0.4f else 1f).clip(RoundedCornerShape(28.dp)).background(c.basic50).padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = Type.bodyLarge, color = c.textPrimary)
            if (sub != null) Text(sub, style = Type.labelMedium, color = c.textSecondary)
        }
        trailing()
    }
}

/** 디자인 시스템에 맞춘 켜고 끄는 스위치: 켜짐은 짙은 바탕에 흰 동그라미가 오른쪽 */
@Composable
private fun PillSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = duckColors
    Box(
        Modifier.width(80.dp).height(48.dp).clip(CircleShape)
            .background(if (checked) c.basic900 else c.basic300)
            .clickable { onChange(!checked) }
            .semantics { contentDescription = if (checked) "켜짐" else "꺼짐" },
    ) {
        Box(
            Modifier.padding(4.dp).size(40.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape).background(c.basic00),
        )
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = duckColors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = Type.bodyLarge, color = c.textPrimary)
            Text(sub, style = Type.labelMedium, color = c.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 형용사를 목록에서 고른다 (자유 입력 없음). 7개 묶음 */
@Composable
private fun AdjectiveChips(selected: String?, onSelect: (String) -> Unit) {
    val c = duckColors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdjectiveGroup.values().forEach { g ->
            Text(g.label, style = Type.labelMedium, color = c.textSecondary)
            g.words.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { w ->
                        val on = w == selected
                        Box(
                            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (on) c.buttonFilled else c.surfaceHigh)
                                .clickable { onSelect(w) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(w, style = Type.bodySmall, color = if (on) c.onButtonFilled else c.textPrimary, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------- 시작: 닉네임 정하기 ----------------

/** 처음 켰을 때 닉네임만 정하는 화면. 마음에 드는 이름이 나올 때까지 다시 뽑고 [시작하기]를 누르면 바로 메인이다 */
@Composable
private fun NicknameStart(state: DuckDuckState, controller: DuckDuckController, modifier: Modifier) {
    val c = duckColors
    var adjective by remember { mutableStateOf(NicknameAdjectives.randomDefault()) }
    var spins by remember { mutableIntStateOf(0) }
    val angle by animateFloatAsState(spins * 360f, tween(450), label = "start-shuffle")
    Column(
        modifier.fillMaxSize().background(c.basic00).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // 앱 아이콘
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.app_icon), contentDescription = "앱 아이콘",
            modifier = Modifier.appear(0).size(120.dp).clip(RoundedCornerShape(34.dp)),
        )
        Spacer(Modifier.height(56.dp))
        Text("처음 오셨군요!\n닉네임을 정해주세요", style = Type.titleLarge.copy(lineHeight = 52.sp), color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.appear(1))
        Spacer(Modifier.height(20.dp))
        Text("설정에서 언제든 바꿀 수 있어요", style = Type.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.appear(2))
        Spacer(Modifier.height(120.dp))
        Row(modifier = Modifier.appear(3), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            // 닉네임 칸: 이름과 다시 뽑기(셔플) 아이콘
            Row(
                Modifier.width(460.dp).height(96.dp).clip(RoundedCornerShape(28.dp)).background(c.basic50).padding(start = 40.dp, end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$adjective ${state.modelName}", style = Type.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .clickable { adjective = NicknameAdjectives.all.filter { it != adjective }.random(); spins++ }
                        .semantics { contentDescription = "닉네임 다시 뽑기" },
                    contentAlignment = Alignment.Center,
                ) { PhIcon(R.drawable.ph_arrows_clockwise, c.basic600, size = 44.dp, modifier = Modifier.graphicsLayer { rotationZ = angle }) }
            }
            BasicButton("시작하기", onClick = { controller.completeOnboarding(adjective) }, modifier = Modifier.width(176.dp), height = 90.dp)
        }
    }
}
