package com.example.chargesuggest.duck.pleos

import android.annotation.SuppressLint
import android.car.Car
import android.car.CarInfoManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chargesuggest.duck.domain.CarNameInfo
import com.example.chargesuggest.duck.ModelNameSource

/**
 * AAOS 표준 차량 정보(CarInfoManager)에서 제조사·모델명·연식을 읽는다 (권한 android.car.permission.CAR_INFO).
 * 차 한 대를 특정하는 값이 아니라서 차대번호보다 개인정보 부담이 적다.
 * TODO(verify): 실제 Pleos 차량이 모델명을 채워 주는지 확인해야 한다.
 */
class AaosCarInfoSource(context: Context) : ModelNameSource {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("NewApi") // Car.createCar(..., listener)는 API 29부터. 아래에서 버전을 확인하고 부른다
    override fun read(onResult: (CarNameInfo?) -> Unit) {
        if (Build.VERSION.SDK_INT < 29) { onResult(null); return }
        runCatching {
            Car.createCar(app, main, CONNECT_TIMEOUT_MS, Car.CarServiceLifecycleListener { car, ready ->
                if (!ready) return@CarServiceLifecycleListener
                val manager = car.getCarManager(Car.INFO_SERVICE) as? CarInfoManager
                val info = runCatching { CarNameInfo(manager?.manufacturer, manager?.model, manager?.modelYear) }
                    .onFailure { Log.w(TAG, "차량 정보 읽기 실패: ${it.javaClass.simpleName}") }
                    .getOrNull()
                main.post { onResult(info); runCatching { car.disconnect() } }
            })
        }.onFailure {
            Log.w(TAG, "Car 서비스 연결 실패", it)
            main.post { onResult(null) }
        }
    }

    private companion object {
        const val TAG = "DuckCarInfo"
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
