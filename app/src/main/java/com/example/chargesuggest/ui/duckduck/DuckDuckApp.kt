package com.example.chargesuggest.ui.duckduck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.chargesuggest.duckduck.physics.DriveMotion
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.DuckDuckViewModel
import com.example.chargesuggest.duckduck.WeatherPreset
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.ui.duck.BasicButton
import com.example.chargesuggest.ui.duck.DuckTheme
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.duckColors
import kotlinx.coroutines.delay

/**
 * Duck Duck 앱의 루트. 창 너비가 넓으면(약 1200dp 이상, Landscape 1682) 주차 화면이 두 칸으로 나뉘고,
 * 좁으면(Portrait 841) 탭으로 전환한다. 기준은 방향이 아니라 창 너비다.
 * 아래쪽 시연 조작부는 제출 전에 실제 차량 값으로 바꾸거나 제거한다.
 */
@Composable
fun DuckDuckApp(vm: DuckDuckViewModel, forceWidthDp: Int? = null) {
    DuckTheme {
        val c = duckColors
        val state by vm.controller.state.collectAsState()
        val binHolder = remember(vm) { DuckBinHolder(vm.motion) }
        // 정차 판정(2초)과 카드 만료는 시간이 흘러야 반영되므로 0.5초마다 알려 준다
        LaunchedEffect(Unit) { while (true) { vm.controller.tick(); delay(500) } }
        BoxWithConstraints(Modifier.fillMaxSize().background(c.basic50)) {
            // forceWidthDp: Portrait(841) 같은 다른 창 너비를 한 화면에서 확인하기 위한 확인용 값
            val w = forceWidthDp?.dp ?: maxWidth
            val wide = w >= 1200.dp
            // 시연 조작부는 처음에 접어 둬서 메인 화면이 와이어프레임처럼 창 전체를 쓰게 한다
            LaunchedEffect(Unit) { vm.demo.controlsOpen = false }
            Box(Modifier.fillMaxHeight().width(w)) {
                DuckDuckService(state, vm.controller, wide, Modifier.fillMaxSize(), binHolder)
                // 시연 리모콘(서버 대신 시연하려고 둔 조작): 접혀 있으면 작은 버튼, 펼치면 네 개 버튼이 화면 아래에 뜬다
                if (vm.demo.controlsOpen) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                        if (DuckConfig.FULL_DEMO_PANEL) ControlStrip(vm, state, compact = !wide)
                        else DemoRemote(vm, state)
                    }
                } else {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 4.dp).height(40.dp)
                            .clip(RoundedCornerShape(20.dp)).background(c.surfaceHigh)
                            .clickable { vm.demo.controlsOpen = true }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("시연 모드", style = Type.labelSmall, color = c.textSecondary) }
                }
            }
        }
    }
}

/**
 * 시연 리모콘: 주행 / 정차 / 근처 차 발견 / 오리 받기 / 처음으로. 순서 없이 아무 때나 자유롭게 누른다.
 *  - 주행·정차: 지금 상태가 진하게 표시된다. 주행을 누르면 약 3초 뒤 방지턱을 넘는 모션이 한 번 나온다.
 *  - 근처 차 발견: 근처에 같은 차가 있는 상태를 켜고 끈다(켜져 있으면 진하게). 시스템은 알리지 않고, 오리를 던져야 전달된다.
 *  - 오리 받기: 누를 때마다 종류가 겹치지 않게 차례로 받는다.
 *  - 처음으로: 닉네임 정하기부터 처음 상태로 되돌린다.
 */
@Composable
private fun DemoRemote(vm: DuckDuckViewModel, state: DuckDuckState) {
    val c = duckColors
    val d = vm.demo
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.basic00.copy(alpha = 0.94f)).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("시연 모드", style = Type.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("접기", style = Type.labelMedium, color = c.textSecondary, modifier = Modifier.clickable { d.controlsOpen = false }.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("주행", state.mode == DriveMode.DRIVING, Modifier.weight(1f)) { vm.demoStartDriving() }
            Chip("정차", state.mode == DriveMode.STOPPED, Modifier.weight(1f)) { vm.demoStop() }
            Chip("근처 차 발견", d.nearbyCars > 0, Modifier.weight(1.3f)) { vm.demoToggleNearby() }
            Chip("오리 받기", false, Modifier.weight(1f)) { vm.demoReceive() }
            Chip("처음으로", false, Modifier.weight(1f)) { vm.resetSaved() }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    BasicButton(label, onClick, modifier, filled = on, height = 60.dp)

@Composable
private fun ControlStrip(vm: DuckDuckViewModel, state: DuckDuckState, compact: Boolean) {
    val c = duckColors
    val d = vm.demo
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.basic00).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { d.controlsOpen = !d.controlsOpen }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "시연 조작 (제출 전 제거) · 날씨: ${d.lastWeatherLabel}",
                style = Type.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f),
            )
            Text(if (d.controlsOpen) "접기" else "펼치기", style = Type.bodyMedium, color = c.textSecondary)
        }
        if (!d.controlsOpen) return@Column

        @Composable
        fun line(content: @Composable RowScope.() -> Unit) =
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)

        // 주행 상태: 차량 연동 또는 수동
        val drive: @Composable RowScope.() -> Unit = {
            Chip("차량 연동", d.driveFromVehicle, Modifier.weight(1.3f)) { vm.useVehicleDriveState(!d.driveFromVehicle) }
            Chip("주행(D)", !d.driveFromVehicle && state.mode == DriveMode.DRIVING, Modifier.weight(1f)) { vm.setMode(DriveMode.DRIVING) }
            Chip("정차", !d.driveFromVehicle && state.mode == DriveMode.STOPPED, Modifier.weight(1f)) { vm.setMode(DriveMode.STOPPED) }
            Chip("주차(P)", !d.driveFromVehicle && state.mode == DriveMode.PARKED, Modifier.weight(1f)) { vm.setMode(DriveMode.PARKED) }
        }
        line(drive)

        // 날씨 주입
        val presets = WeatherPreset.values().toList()
        presets.chunked(if (compact) 4 else 7).forEach { row ->
            line { row.forEach { p -> Chip(p.label, d.lastWeatherLabel == p.label, Modifier.weight(1f)) { vm.injectWeather(p) } } }
        }

        // 주행 중 차의 움직임(오리 통이 쏠리고 튄다). 주행(D)에서 누른다
        line {
            Chip("출발", false, Modifier.weight(1f)) { vm.driveEvent(DriveMotion.Event.ACCELERATE) }
            Chip("좌회전", false, Modifier.weight(1f)) { vm.driveEvent(DriveMotion.Event.TURN_LEFT) }
            Chip("우회전", false, Modifier.weight(1f)) { vm.driveEvent(DriveMotion.Event.TURN_RIGHT) }
            Chip("급정지", false, Modifier.weight(1f)) { vm.driveEvent(DriveMotion.Event.BRAKE) }
            Chip("요철", false, Modifier.weight(1f)) { vm.driveEvent(DriveMotion.Event.BUMP) }
            Chip("자동 모션", d.autoMotion, Modifier.weight(1.3f)) { vm.setAutoMotion(!d.autoMotion) }
        }

        // 이벤트
        line {
            // 시스템은 알리지 않는다. 눈에 보이지 않게 "주변에 같은 차가 있는 상태"를 켜고 끈다(운전자가 오리를 던졌을 때만 쓰인다)
            Chip(if (d.nearbyCars > 0) "주변 같은 차 있음(끄기)" else "주변에 같은 차 두기", d.nearbyCars > 0, Modifier.weight(1.4f)) {
                if (d.nearbyCars > 0) vm.simulateAllLost() else vm.simulateFound()
            }
            Chip("오리 받기", false, Modifier.weight(1f)) { vm.simulateReceived() }
            Chip("내비 안내 중", d.naviSpeaking, Modifier.weight(1f)) { d.naviSpeaking = !d.naviSpeaking }
            Chip("날씨 오리 선물", false, Modifier.weight(1f)) { vm.grantWeatherDuck() }
        }
        line {
            Chip("상대 꺼짐", d.receiverOffline, Modifier.weight(1f)) { vm.setReceiverOffline(!d.receiverOffline) }
            Chip("무지개 받기", false, Modifier.weight(1f)) { vm.simulateReceived(DuckType.RAINBOW) }
            Chip("처음부터", false, Modifier.weight(1f)) { vm.resetSaved() }
        }
    }
}
