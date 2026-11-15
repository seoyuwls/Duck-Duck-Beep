package com.example.chargesuggest.duck.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chargesuggest.duckduck.physics.DriveMotion

/**
 * 실제 차량의 조향각과 브레이크 페달을 오리 통의 움직임(DriveMotion)에 전달한다.
 *  - 조향각: Pleos Vehicle SDK SteeringWheel.registerSteeringWheelAngle (권한 CAR_DRIVE)
 *  - 브레이크: Pleos Vehicle SDK Brake.registerBrakePedalStatus (권한 CAR_DRIVE, 밟힘 여부만 온다)
 * 값은 메인 스레드로 넘긴 뒤 DriveMotion에 넣는다. 연결이 실패해도 앱은 계속 동작한다(시연 조작과 자동 모션은 그대로 쓸 수 있다).
 */
class PleosMotionSource(context: Context, private val motion: DriveMotion) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null

    private val steering = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) { main.post { motion.setSteering(angle) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "조향각 구독 실패: ${e.message}") }
    }
    private val brake = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) { main.post { motion.setBrake(status) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "브레이크 구독 실패: ${e.message}") }
    }

    fun start() {
        runCatching {
            val v = Vehicle(app).also { it.initialize() }
            vehicle = v
            v.getSteeringWheel().registerSteeringWheelAngle(steering)
            v.getBrake().registerBrakePedalStatus(brake)
        }.onFailure { Log.w(TAG, "Vehicle SDK 연결 실패", it) }
    }

    fun stop() {
        runCatching { vehicle?.getSteeringWheel()?.unregisterSteeringWheelAngle(steering) }
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brake) }
        runCatching { vehicle?.release() }
        vehicle = null
    }

    private companion object { const val TAG = "PleosMotionSource" }
}
