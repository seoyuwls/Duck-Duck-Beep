package com.example.chargesuggest.duck.pleos

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.chargesuggest.duck.VinSource

/**
 * Pleos Vehicle SDK의 CarInfo로 차대번호를 읽는다 (권한 CAR_INFO).
 * 차대번호는 차량 한 대를 특정하는 값이라 여기서는 로그에 남기지 않고 결과만 넘긴다.
 * 에뮬레이터는 자리표시 값("invalid")을 돌려주므로 실제 차종 구분은 실차에서 확인해야 한다. TODO(verify)
 */
class PleosVinSource(context: Context) : VinSource {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    override fun read(onResult: (String?) -> Unit) {
        runCatching {
            val vehicle = Vehicle(app).also { it.initialize() }
            vehicle.getCarInfo().getVINInfo(
                { vin -> main.post { onResult(vin); runCatching { vehicle.release() } } },
                { e ->
                    Log.w(TAG, "차대번호 읽기 실패: ${e.javaClass.simpleName}")
                    main.post { onResult(null); runCatching { vehicle.release() } }
                },
            )
        }.onFailure {
            Log.w(TAG, "Vehicle SDK 연결 실패", it)
            main.post { onResult(null) }
        }
    }

    private companion object { const val TAG = "DuckVin" }
}
