package com.example.chargesuggest.ui.duck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.DuckReactor
import com.example.chargesuggest.duck.DuckViewModel
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.DuckContext
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.DuckRoller
import com.example.chargesuggest.duck.domain.Rarity
import com.example.chargesuggest.duck.domain.ReactionPlan
import kotlinx.coroutines.delay
import kotlin.random.Random

/*
 * 시연 전용 코드. 제출 전에 조작부(ControlStrip)는 실제 차량 값으로 바꾸거나 제거한다.
 *
 * 화면은 차 한 대의 서비스 화면 하나만 보여 준다. 시연 흐름:
 *   [보내는 차 시점] 같은 차 발견 -> "전달하기" -> "전달했어요"
 *   -> [받는 차 시점]으로 자동 전환 -> 수령 카드 + 조명 + 효과음 -> (정차/주차로 바꿔) 도감 확인
 * 보내는 차와 받는 차를 동시에 보여 주지 않는다.
 */

/** 어느 차의 화면을 보여 줄지 (시연 시점) */
enum class Viewpoint { SENDER, RECEIVER }

/** 화면 폭 시연: 자동은 실제 창 너비를 따르고, 나머지는 해당 폭으로 고정해 배치 변화를 보여 준다 */
enum class LayoutMode(val label: String, val widthDp: Int?) {
    AUTO("자동", null),
    PORTRAIT("Portrait", 841),       // 창 너비 841dp
    LANDSCAPE("Landscape", 1682),    // 창 너비 1682dp
}

/** 시연 조작 값 */
@Stable
class DemoState {
    var viewpoint by mutableStateOf(Viewpoint.SENDER)
    var layoutMode by mutableStateOf(LayoutMode.AUTO)
    var naviSpeaking by mutableStateOf(false)
    var receiverAppHidden by mutableStateOf(false)
    var nearbyFound by mutableStateOf(true)
    /** true면 근처 같은 차를 가짜 차 두 대(시뮬레이션)로 인식하고, false면 "같은 차 발견" 버튼으로 정한다 */
    var nearbySim by mutableStateOf(false)
    /** 시뮬레이션 상태 한 줄 (봇별 거리와 발견 여부) */
    var simStatus by mutableStateOf("")
    var controlsOpen by mutableStateOf(true)
    /** true면 주행 상태를 실제 차량(기어 + 주행 규제)에서 받는다. 수동 버튼을 누르면 false가 된다 */
    var driveFromVehicle by mutableStateOf(true)
    /** 차량이 마지막으로 알려 준 주행 상태 (아직 못 받았으면 null) */
    var vehicleMode by mutableStateOf<DriveMode?>(null)

    // 아래는 종류가 여럿일 때만 쓴다 (DuckCatalog.DEMO_BASIC_ONLY = false)
    var rainy by mutableStateOf(false)
    var charging by mutableStateOf(false)
    var family by mutableStateOf(false)
    var forcedRarity by mutableStateOf<Rarity?>(null)

    fun contexts(): Set<DuckContext> = buildSet {
        if (rainy) add(DuckContext.RAINY)
        if (charging) add(DuckContext.CHARGING)
        if (family) add(DuckContext.FAMILY)
    }

    /** 시연에서 특정 등급을 바로 보여 주기 위해 고른 인형 (조건 없는 인형 중 첫 번째) */
    fun forcedKind(): DuckKind? =
        forcedRarity?.let { r -> DuckCatalog.active.firstOrNull { it.rarity == r && it.requires == null } }
}

/** 시연용 뽑기: 등급을 고정해 두었으면 그 인형을, 아니면 실제와 같은 랜덤 */
class DemoRoller(private val demo: DemoState) : DuckRoller(DuckCatalog.active) {
    override fun roll(contexts: Set<DuckContext>, rng: Random): DuckKind =
        demo.forcedKind() ?: super.roll(contexts, rng)
}

/** 화면에 "조명이 켜졌고 소리가 났다"를 보여 주기 위한 기록 */
data class ReactionStamp(val seq: Int, val plan: ReactionPlan)

/**
 * 시연용 반응기. 화면 표시를 위해 계획을 기록하고, delegate가 있으면 실제 차량에도 보낸다.
 * (보내는 차 쪽은 delegate 없이 화면 표시만, 받는 차 쪽은 실제 조명·효과음까지 낸다)
 */
class DemoReactor(private val delegate: DuckReactor?) : DuckReactor {
    private var n = 0
    var last by mutableStateOf<ReactionStamp?>(null)
        private set

    override fun react(plan: ReactionPlan) {
        if (plan != ReactionPlan.NONE) last = ReactionStamp(++n, plan)
        runCatching { delegate?.react(plan) }
    }
}

/** 조명이 켜진 동안 색 막대와 효과음 볼륨을 보여 준다 */
@Composable
fun AmbientIndicator(reactor: DemoReactor) {
    val c = duckColors
    val stamp = reactor.last
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(stamp?.seq) {
        if (stamp != null) { visible = true; delay(1_500); visible = false }
    }
    val rgb = stamp?.plan?.ambientRgb
    Column(Modifier.fillMaxWidth().height(56.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (visible && rgb != null && stamp != null) {
            // 흰색도 보이도록 테두리를 둔다
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF000000.toInt() or rgb))
                    .border(2.dp, c.basic300, RoundedCornerShape(7.dp)),
            )
            Text(
                "실내 조명 + 효과음 (볼륨 ${(stamp.plan.soundVolume * 100).toInt()}%)",
                style = DuckType.labelMedium, color = c.textSecondary,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.surfaceHigh))
            Text("실내 조명 꺼짐", style = DuckType.labelMedium, color = c.textTertiary)
        }
    }
}

// ---------------- 시연 조작부 ----------------

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    BasicButton(label, onClick, modifier, filled = on, height = 60.dp)

private class Flag(val label: String, val on: Boolean, val onClick: () -> Unit)

@Composable
private fun RowScope.ModeGroup(label: String, mode: DriveMode, set: (DriveMode) -> Unit) {
    val c = duckColors
    Text(label, style = DuckType.bodyMedium, color = c.textSecondary)
    Toggle("주행(D)", mode == DriveMode.DRIVING, Modifier.weight(1f)) { set(DriveMode.DRIVING) }
    Toggle("정차", mode == DriveMode.STOPPED, Modifier.weight(1f)) { set(DriveMode.STOPPED) }
    Toggle("주차(P)", mode == DriveMode.PARKED, Modifier.weight(1f)) { set(DriveMode.PARKED) }
}

/** compact=true(좁은 창)이면 줄을 더 잘게 나눠서 글자가 깨지지 않게 한다 */
@Composable
fun ControlStrip(vm: DuckViewModel, mode: DriveMode, compact: Boolean) {
    val c = duckColors
    val d = vm.demo
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.basic00).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { d.controlsOpen = !d.controlsOpen }, verticalAlignment = Alignment.CenterVertically) {
            Text("시연 조작 (제출 전 제거)", style = DuckType.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(if (d.controlsOpen) "접기" else "펼치기", style = DuckType.bodyMedium, color = c.textSecondary)
        }
        if (!d.controlsOpen) return@Column

        @Composable
        fun line(content: @Composable RowScope.() -> Unit) = Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically, content = content,
        )

        // 시점과 주행 상태 (지금 보이는 차 기준)
        val viewpoint: @Composable RowScope.() -> Unit = {
            Text("시점", style = DuckType.bodyMedium, color = c.textSecondary)
            Toggle("보내는 차", d.viewpoint == Viewpoint.SENDER, Modifier.weight(1f)) { vm.showSender() }
            Toggle("받는 차", d.viewpoint == Viewpoint.RECEIVER, Modifier.weight(1f)) { vm.showReceiver() }
        }
        val driveMode: @Composable RowScope.() -> Unit = { ModeGroup("주행 상태", mode) { vm.setCurrentDriveMode(it) } }
        // 주행 상태를 실제 차량(기어 + 주행 규제)에서 받는다. 위 버튼을 누르면 수동으로 바뀐다
        val vehicleLabel = d.vehicleMode?.let { m ->
            when (m) { DriveMode.DRIVING -> "D"; DriveMode.STOPPED -> "정차"; DriveMode.PARKED -> "P" }
        }
        val vehicleLink: @Composable RowScope.() -> Unit = {
            Toggle(
                if (vehicleLabel != null) "차량 연동 (지금 $vehicleLabel)" else "차량 연동 (값 없음)",
                d.driveFromVehicle, Modifier.weight(1f),
            ) { vm.useVehicleDriveState(!d.driveFromVehicle) }
        }
        if (compact) { line(viewpoint); line(driveMode); line(vehicleLink) }
        else { line { viewpoint(); Spacer(Modifier.width(16.dp)); driveMode() }; line(vehicleLink) }

        // 발견 / 내비 / 받는 차 앱 숨김
        val flags = listOf(
            Flag(if (d.nearbySim) "같은 차 발견 (자동)" else "같은 차 발견", d.nearbyFound && !d.nearbySim) { vm.setNearbyFound(!d.nearbyFound) },
            Flag("내비 안내 중", d.naviSpeaking) { d.naviSpeaking = !d.naviSpeaking },
            Flag("받는 차 앱 숨김", d.receiverAppHidden) { d.receiverAppHidden = !d.receiverAppHidden },
        )
        line { flags.forEach { f -> Toggle(f.label, f.on, Modifier.weight(1f), f.onClick) } }

        // 근처 인식: 버튼 / 시뮬레이션 (가짜 차 두 대가 지나가며 서버 규칙으로 같은 차종만 알려 준다)
        line {
            Toggle(if (d.nearbySim) "근처 인식: 시뮬레이션 켜짐" else "근처 인식: 시뮬레이션 켜기", d.nearbySim, Modifier.weight(1f)) { vm.setNearbySim(!d.nearbySim) }
            if (d.nearbySim) Toggle("시뮬레이션 다시 시작", false, Modifier.weight(1f)) { vm.restartNearbySim() }
        }
        if (d.nearbySim && d.simStatus.isNotEmpty()) {
            Text(d.simStatus, style = DuckType.labelMedium, color = c.textSecondary)
        }

        // 저장된 도감과 보낸 개수를 비운다 (시연을 처음부터 다시 할 때)
        line { Toggle("도감·기록 초기화", false, Modifier.weight(1f)) { vm.resetData() } }

        // 화면 폭: 서비스의 Portrait / Landscape 배치 변화를 보여 준다
        line {
            Text("화면 폭", style = DuckType.bodyMedium, color = c.textSecondary)
            LayoutMode.values().forEach { m -> Toggle(m.label, d.layoutMode == m, Modifier.weight(1f)) { d.layoutMode = m } }
        }

        // 종류가 여럿일 때만: 상황 / 다음 인형 등급 (DuckCatalog.DEMO_BASIC_ONLY)
        if (DuckCatalog.active.size > 1) {
            line {
                Text("상황", style = DuckType.bodyMedium, color = c.textSecondary)
                Toggle("비", d.rainy, Modifier.weight(1f)) { d.rainy = !d.rainy }
                Toggle("충전", d.charging, Modifier.weight(1f)) { d.charging = !d.charging }
                Toggle("가족", d.family, Modifier.weight(1f)) { d.family = !d.family }
            }
            line {
                Text("다음", style = DuckType.bodyMedium, color = c.textSecondary)
                Toggle("랜덤", d.forcedRarity == null, Modifier.weight(1f)) { d.forcedRarity = null }
                Rarity.values().forEach { r -> Toggle(r.label, d.forcedRarity == r, Modifier.weight(1f)) { d.forcedRarity = r } }
            }
        }
    }
}

// ---------------- 앱 전체 ----------------

/**
 * 차 한 대의 서비스 화면 하나를 보여 준다. 창 너비가 약 1200dp 이상이면 Landscape 배치(메인 + 도감·설정),
 * 아니면 Portrait 배치(탭)다. 기준은 방향이 아니라 창 너비다.
 * 시점(보내는 차/받는 차)은 시연 흐름에 따라 바뀌지만 한 번에 한 대만 보인다.
 */
@Composable
fun DuckApp(vm: DuckViewModel) {
    DuckTheme {
        val c = duckColors
        val d = vm.demo
        val meState by vm.me.state.collectAsState()
        val otherState by vm.other.state.collectAsState()
        BoxWithConstraints(Modifier.fillMaxSize().background(c.basic50)) {
            // 화면 폭 시연: 선택한 폭으로 고정하되 실제 창보다 넓어지지는 않는다
            val forced = d.layoutMode.widthDp?.dp
            val w = if (forced == null || forced > maxWidth) maxWidth else forced
            val wide = w >= 1200.dp
            // 좁은 배치에서는 조작부를 접은 채로 시작해 서비스 화면이 가려지지 않게 한다
            LaunchedEffect(wide) { d.controlsOpen = wide }

            val receiver = d.viewpoint == Viewpoint.RECEIVER
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxHeight().width(w).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 시점이 바뀌면 화면 상태(탭 선택 등)를 처음부터 다시 시작한다
                    key(d.viewpoint) {
                        CarView(
                            controller = if (receiver) vm.other else vm.me,
                            state = if (receiver) otherState else meState,
                            reactor = if (receiver) vm.otherReactor else vm.meReactor,
                            carModel = vm.carModel,
                            wide = wide,
                            demoTag = if (receiver) "시연 시점: 받는 차" else "시연 시점: 보내는 차",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    ControlStrip(vm, if (receiver) otherState.driveMode else meState.driveMode, compact = !wide)
                }
            }
        }
    }
}
