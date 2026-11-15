package com.example.chargesuggest

import androidx.lifecycle.ViewModel
import com.example.chargesuggest.domain.Advice
import com.example.chargesuggest.domain.ChargeAdvisor
import com.example.chargesuggest.domain.DriveState
import com.example.chargesuggest.ports.BatterySource
import com.example.chargesuggest.ports.ChargerRoute
import com.example.chargesuggest.ports.DriveStateSource
import com.example.chargesuggest.ports.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 화면이 그려야 할 모든 것. 화면은 이 값만 보고 그린다. */
data class UiState(
    val soc: Int = 100,
    val driveState: DriveState = DriveState.PARKED,
    val advice: Advice = Advice.None,
    val routeStatus: RouteStatus = RouteStatus.IDLE,
)

enum class RouteStatus { IDLE, ADDED, FAILED }

class ChargeViewModel(
    private val battery: BatterySource,
    private val driveSource: DriveStateSource,
    private val speaker: Speaker,
    private val route: ChargerRoute,
    private val advisor: ChargeAdvisor = ChargeAdvisor(),
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 화면이 보이기 시작할 때(onStart): 차량 값 구독을 연다. */
    fun start() {
        driveSource.start { drive -> _state.update { it.copy(driveState = drive) } }
        battery.start { soc -> onSoc(soc) }
    }

    /** 화면이 가려질 때(onStop): 구독을 끊어 성능 기준(CPU, RAM)을 지킨다. */
    fun stop() {
        battery.stop()
        driveSource.stop()
    }

    private fun onSoc(soc: Int) {
        val advice = advisor.evaluate(soc)
        _state.update { current ->
            current.copy(
                soc = soc,
                // 이미 떠 있는 제안은 사용자가 답하기 전까지 유지한다
                advice = if (advice is Advice.SuggestCharge) advice else current.advice,
            )
        }
        if (advice is Advice.SuggestCharge) {
            speaker.speak("배터리가 ${soc}%예요. 가까운 충전소를 경로에 넣을까요?")
        }
    }

    /** "충전소 추가"를 눌렀을 때: 결과가 차량 내비로 이어진다. */
    fun accept() {
        route.addNearestCharger(
            onDone = { _state.update { it.copy(advice = Advice.None, routeStatus = RouteStatus.ADDED) } },
            onFailed = { _state.update { it.copy(routeStatus = RouteStatus.FAILED) } },
        )
    }

    /** "나중에"를 눌렀을 때. */
    fun dismiss() {
        _state.update { it.copy(advice = Advice.None) }
    }
}
