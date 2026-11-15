package com.example.chargesuggest.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 앱이 화면에 보이지 않을 때 "서버에서 인형이 도착했다"를 흉내 내는 수신기.
 *  adb shell am broadcast -n com.example.chargesuggest/.probe.DuckReceiver --es what ambient|sound|plain|message|all --ei color 3
 */
class DuckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val what = intent.getStringExtra("what") ?: "all"
        val color = intent.getIntExtra("color", 3)
        Log.d("PleosProbe", "[오리] 수신기 실행: what=$what color=$color (앱 화면 없이)")
        val r = DuckReaction(context.applicationContext)
        if (what == "ambient" || what == "all") r.ambient(color)
        if (what == "sound" || what == "all") r.sound()
        if (what == "plain" || what == "all") r.notifyPlain()
        if (what == "message") r.notifyMessage()
        if (what == "vin") r.vin()
        // 붙여 받은 방식(CarPropertyManager + VehiclePropertyIds)을 그대로 시험한다. 막힐 수 있어서 별도 스레드에서
        if (what == "props") {
            val app = context.applicationContext
            Thread {
                val t0 = System.currentTimeMillis()
                val car = runCatching { android.car.Car.createCar(app) }.getOrNull()
                Log.d("PleosProbe", "[오리] props: createCar -> ${if (car == null) "null" else "연결=${car.isConnected}"} (${System.currentTimeMillis() - t0}ms)")
                val pm = runCatching { car?.getCarManager(android.car.Car.PROPERTY_SERVICE) as? android.car.hardware.property.CarPropertyManager }.getOrNull()
                Log.d("PleosProbe", "[오리] props: CarPropertyManager=${pm != null}")
                for ((name, id) in listOf(
                    "INFO_MAKE" to android.car.VehiclePropertyIds.INFO_MAKE,
                    "INFO_MODEL" to android.car.VehiclePropertyIds.INFO_MODEL,
                    "INFO_MODEL_YEAR" to android.car.VehiclePropertyIds.INFO_MODEL_YEAR,
                    "INFO_MODEL_TRIM" to android.car.VehiclePropertyIds.INFO_MODEL_TRIM,
                )) {
                    val avail = runCatching { pm?.isPropertyAvailable(id, 0) }.getOrNull()
                    val r = runCatching {
                        val v = pm?.getProperty<Any>(id, 0)
                        "값=${v?.value} 상태=${v?.status}"
                    }.getOrElse { "예외 ${it.javaClass.simpleName}: ${it.message?.take(80)}" }
                    Log.d("PleosProbe", "[오리] props: $name 사용가능=$avail $r")
                }
                runCatching { car?.disconnect() }
            }.start()
        }
        if (what == "carinfo") {
            com.example.chargesuggest.duck.pleos.AaosCarInfoSource(context.applicationContext).read { i ->
                Log.d("PleosProbe", "[오리] CarInfoManager: 제조사=${i?.manufacturer} 모델=${i?.model} 연식=${i?.modelYear}")
            }
        }
    }
}
