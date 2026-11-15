package com.example.chargesuggest.duck.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chargesuggest.duck.DriveStateSource
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DriveStateResolver
import com.example.chargesuggest.duck.domain.GearPosition

/**
 * 실제 차량 값으로 주행 상태를 알려 준다.
 *  - 기어: Pleos Vehicle SDK의 Gear (권한 CAR_DRIVE)
 *  - 주행 규제(DO): AAOS Car API의 CarUxRestrictionsManager. 속도로 정해지는 플랫폼 규칙이라 속도 API를 따로 쓰지 않는다.
 * 두 값을 DriveStateResolver로 합쳐서, 값이 바뀔 때만 메인 스레드에서 알린다.
 *
 * TODO(verify): 주행 규제가 "정차" 상태(기어 D, 속도 0)에서 실제로 풀리는지는 차량 설정에 따라 다르다. 에뮬레이터 값으로 확인한다.
 */
class PleosDriveStateSource(context: Context) : DriveStateSource {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null

    private var gear = GearPosition.UNKNOWN
    private var requiresDo: Boolean? = null
    private var last: DriveMode? = null
    private var callback: ((DriveMode) -> Unit)? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            main.post { this@PleosDriveStateSource.gear = toPosition(gear); publish() }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "기어 구독 실패: ${e.message}") }
    }

    @SuppressLint("NewApi") // Car.createCar(..., listener)는 API 29부터. 아래에서 버전을 확인하고 부른다
    override fun start(onChanged: (DriveMode) -> Unit) {
        callback = onChanged

        // 1) 기어
        runCatching {
            val v = Vehicle(app).also { it.initialize() }
            vehicle = v
            val g = v.getGear()
            g.getCurrentGearSelectionState(
                { vg -> main.post { gear = toPosition(vg); publish() } },
                { e -> Log.w(TAG, "기어 읽기 실패: ${e.message}") },
            )
            g.registerGearSelection(gearListener)
        }.onFailure { Log.w(TAG, "Vehicle SDK 연결 실패", it) }

        // 2) 주행 규제
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                car = Car.createCar(app, main, CONNECT_TIMEOUT_MS, Car.CarServiceLifecycleListener { c, ready ->
                    if (!ready) { uxManager = null; requiresDo = null; publish(); return@CarServiceLifecycleListener }
                    val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    uxManager = m
                    m?.registerListener { r -> requiresDo = r.isRequiresDistractionOptimization; publish() }
                    requiresDo = m?.currentCarUxRestrictions?.isRequiresDistractionOptimization
                    publish()
                })
            }.onFailure { Log.w(TAG, "Car 서비스 연결 실패", it) }
        }
    }

    override fun stop() {
        runCatching { vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.release() }
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        vehicle = null; car = null; uxManager = null; callback = null
    }

    private fun publish() {
        val mode = DriveStateResolver.resolve(gear, requiresDo)
        Log.d(TAG, "기어=$gear 주행규제=$requiresDo -> $mode")
        if (mode != last) { last = mode; callback?.invoke(mode) }
    }

    private fun toPosition(g: VehicleGear?): GearPosition = when (g) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
        VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9,
        -> GearPosition.DRIVE
        else -> GearPosition.UNKNOWN
    }

    private companion object {
        const val TAG = "DuckDrive"
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
