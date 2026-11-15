package com.example.chargesuggest.domain

/** 차량이 주차 중인지 주행 중인지. 화면 모양을 정하는 기준이다. */
enum class DriveState { PARKED, DRIVING }

/** 앱이 지금 사용자에게 무엇을 제안해야 하는지. */
sealed interface Advice {
    data object None : Advice
    data class SuggestCharge(val socPercent: Int) : Advice
}

/**
 * 핵심 가치 로직: "배터리가 부족해지면, 한 번만 충전을 제안한다."
 *
 * SDK 의존성이 없는 순수 Kotlin이라서 에뮬레이터 없이 단위 테스트할 수 있다.
 *
 * 같은 제안이 계속 반복되면 운전자를 방해하므로, 한 번 제안한 뒤에는
 * 배터리가 resetThreshold 이상으로 회복될 때까지 다시 제안하지 않는다.
 */
class ChargeAdvisor(
    private val lowThreshold: Int = 20,
    private val resetThreshold: Int = 25,
) {
    private var alreadySuggested = false

    fun evaluate(socPercent: Int): Advice {
        if (socPercent >= resetThreshold) {
            alreadySuggested = false
        }
        if (socPercent <= lowThreshold && !alreadySuggested) {
            alreadySuggested = true
            return Advice.SuggestCharge(socPercent)
        }
        return Advice.None
    }
}
