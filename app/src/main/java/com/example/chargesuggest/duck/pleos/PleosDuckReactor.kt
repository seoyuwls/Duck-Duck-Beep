package com.example.chargesuggest.duck.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chargesuggest.duck.DuckReactor
import com.example.chargesuggest.duck.domain.ReactionPlan

/**
 * 차의 반응을 실제로 낸다: 앰비언트 조명(Vehicle Light) + 짧은 효과음(오디오).
 *
 * 시험에서 확인한 점(에뮬레이터):
 *  - 앰비언트는 좌석 하나씩이 아니라 5개 영역을 한 번에 묶어서 보낼 때만 호출이 성공한다.
 *  - 색은 RGB 정수(0xRRGGBB)로 보인다. 현재 값 읽기는 에뮬레이터에서 갱신되지 않는 것으로 보여
 *    "원래 색으로 되돌리기"가 실제로 동작하는지는 TODO(verify).
 *  - 필요한 권한: pleos.car.permission.CAR_LIGHTS, CONTROL_CAR_LIGHTS
 *  - Vehicle 연결은 처음 호출에 약 4초 걸리므로 앱 시작 때 warmUp()으로 미리 연결한다.
 */
class PleosDuckReactor(context: Context) : DuckReactor {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null

    private val allSeats = SeatArea(
        SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT,
    )

    /** 앱이 켜질 때 미리 차량에 연결해 둔다 */
    fun warmUp() { vehicle() }

    fun release() {
        vehicle?.release()
        vehicle = null
    }

    override fun react(plan: ReactionPlan) {
        plan.ambientRgb?.let { flashAmbient(it) }
        if (plan.playSound) playQuack(plan.soundVolume)
    }

    // ---- 앰비언트 조명 ----
    private fun vehicle(): Vehicle =
        vehicle ?: Vehicle(app).also { it.initialize(); vehicle = it }

    private fun flashAmbient(rgb: Int) {
        val light = vehicle().getLight()
        // 끝난 뒤 되돌릴 수 있게 지금 색을 먼저 읽어 둔다 (못 읽으면 되돌리지 않음)
        light.getAmbientLightBrightnessColor(
            area = allSeats,
            onComplete = { before ->
                setColor(rgb)
                if (before != null) main.postDelayed({ setColor(before) }, FLASH_MS)
            },
            onFailed = { e ->
                Log.w(TAG, "앰비언트 현재 색 읽기 실패: ${e.message}")
                setColor(rgb)
            },
        )
    }

    private fun setColor(rgb: Int) {
        val light = vehicle().getLight()
        light.setAmbientLightBrightnessColor(
            area = allSeats, value = rgb,
            onComplete = { _, _ -> },
            onFailed = { e -> Log.w(TAG, "앰비언트 색 설정 실패: ${e.message}") },
        )
        light.setAmbientLightSwitch(
            area = allSeats, value = LightSwitch.ON,
            onComplete = { _, _ -> },
            onFailed = { e -> Log.w(TAG, "앰비언트 켜기 실패: ${e.message}") },
        )
    }

    // ---- 효과음 ----
    /** 꽥! 소리(res/raw/duck_quack_*.mp3 중 무작위). 오리를 눌렀을 때와 같은 소리다 */
    private val squeaker by lazy { com.example.chargesuggest.ui.duckduck.DuckSqueaker(app) }

    private fun playQuack(volume: Float) {
        squeaker.play(volume.coerceIn(0f, 1f))
    }

    private companion object {
        const val TAG = "DuckReactor"
        const val FLASH_MS = 1_500L
    }
}
