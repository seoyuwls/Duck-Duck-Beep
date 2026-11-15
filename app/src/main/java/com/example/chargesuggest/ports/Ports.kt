package com.example.chargesuggest.ports

import com.example.chargesuggest.domain.DriveState

/*
 * "포트": 앱이 바깥 세계(차량, 음성, 내비)에 바라는 일을 적어 둔 약속이다.
 * 진짜 구현(pleos/)과 가짜 구현(fake/)이 같은 약속을 따르기 때문에,
 * 에뮬레이터 없이도 화면과 로직을 먼저 확인할 수 있다.
 */

/** 배터리 잔량(SOC, %)을 알려 주는 쪽. */
interface BatterySource {
    fun start(onSoc: (Int) -> Unit)
    fun stop() // 백그라운드로 가면 반드시 호출해서 구독을 끊는다
}

/** 주차/주행 상태를 알려 주는 쪽. */
interface DriveStateSource {
    fun start(onState: (DriveState) -> Unit)
    fun stop()
}

/** 음성으로 읽어 주는 쪽 (TTS). */
interface Speaker {
    fun speak(text: String)
}

/** 가까운 충전소를 내비 경유지로 넣는 쪽. */
interface ChargerRoute {
    fun addNearestCharger(onDone: () -> Unit, onFailed: () -> Unit)
}
