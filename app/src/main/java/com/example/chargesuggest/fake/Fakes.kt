package com.example.chargesuggest.fake

import android.util.Log
import com.example.chargesuggest.domain.DriveState
import com.example.chargesuggest.ports.BatterySource
import com.example.chargesuggest.ports.ChargerRoute
import com.example.chargesuggest.ports.DriveStateSource
import com.example.chargesuggest.ports.Speaker

/*
 * 가짜 구현: 화면의 디버그 슬라이더/버튼이 Virtual Console 역할을 대신한다.
 * 실제 차량 연결 전에 흐름(SOC 하락 → 제안 → 경유지 추가)을 확인하는 용도다.
 */

class FakeBatterySource : BatterySource {
    private var listener: ((Int) -> Unit)? = null
    override fun start(onSoc: (Int) -> Unit) { listener = onSoc }
    override fun stop() { listener = null }
    fun set(soc: Int) { listener?.invoke(soc) }
}

class FakeDriveStateSource : DriveStateSource {
    private var listener: ((DriveState) -> Unit)? = null
    override fun start(onState: (DriveState) -> Unit) { listener = onState }
    override fun stop() { listener = null }
    fun set(state: DriveState) { listener?.invoke(state) }
}

class LogSpeaker : Speaker {
    override fun speak(text: String) { Log.d("ChargeSuggest", "TTS: $text") }
}

class FakeChargerRoute : ChargerRoute {
    override fun addNearestCharger(onDone: () -> Unit, onFailed: () -> Unit) {
        Log.d("ChargeSuggest", "경유지 추가(가짜)")
        onDone()
    }
}
