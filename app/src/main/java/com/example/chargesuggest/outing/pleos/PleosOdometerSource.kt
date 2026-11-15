package com.example.chargesuggest.outing.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.content.Context
import com.example.chargesuggest.outing.ports.OdometerSource

/** Vehicle SDK의 계기판 주행거리를 OdometerSource 약속에 맞춘 어댑터 (권한: CAR_DRIVE) */
class PleosOdometerSource(private val context: Context) : OdometerSource {

    private var vehicle: Vehicle? = null
    private var listener: DistanceDrivenListener? = null

    override fun start(onValue: (Float) -> Unit, onError: (String) -> Unit) {
        val v = vehicle ?: Vehicle(context.applicationContext).also { it.initialize(); vehicle = it }
        val odometer = v.getOdometer()
        // 지금 값을 한 번 읽고, 이후 변화는 구독으로 받는다
        odometer.getCurrentDistanceDriven(
            onComplete = { km -> if (km != null) onValue(km) },
            onFailed = { e -> onError("계기판 읽기 실패: ${e.message}") },
        )
        val l = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(distance: Float?) {
                if (distance != null) onValue(distance)
            }

            override fun onFailed(e: Exception) = onError("계기판 구독 실패: ${e.message}")
        }
        listener = l
        odometer.registerDistanceDriven(l)
    }

    override fun stop() {
        listener?.let { vehicle?.getOdometer()?.unregisterDistanceDriven(it) }
        listener = null
        vehicle?.release()
        vehicle = null
    }
}
