package com.example.chargesuggest.pleos

/*
 * 진짜 Pleos SDK와 연결하는 자리. 지금은 뼈대만 있다.
 *
 * 가이드 문서에서 확인된 것 (CLAUDE.md §4.4~4.6):
 *   - Vehicle(context) → initialize() → getXxx() → registerXxx(listener) / unregisterXxx(listener) → release()
 *   - EvBattery는 Vehicle의 도메인이고, 조회 권한은 pleos.car.permission.CAR_ENERGY
 *   - 사용 전에 checkXxxCapabilities로 지원 여부를 확인하고, 미지원이면 대체 UI를 보여준다
 *   - 리스너 등록/해제는 생명주기(onStart/onStop)에 맞추고, 콜백 안에서 release() 하지 않는다
 *
 * 확인되지 않아 채우지 않은 것 (추측 금지):
 *   - EvBattery의 SOC 조회/구독 메서드와 리스너 이름
 *       TODO(verify): https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ (Vehicle > EvBattery)
 *   - 주차/주행 상태(기어) 조회. 가이드상 표준 데이터는 AAOS Car API 우선
 *       TODO(verify): Vehicle DrivingMode 문서 또는 AAOS Car API
 *   - TextToSpeech 권한명, Maven 좌표 (ON_DEVICE 모드 권장)
 *       TODO(verify): TextToSpeech "SDK 가져오기", "권한 선언" 문서
 *   - NaviHelper: 경유지 추가 / 충전 사업자 정보 조회 메서드와 권한명
 *       TODO(verify): NaviHelper 문서
 *
 * 아래 클래스들은 실제 메서드 이름을 확인한 뒤 채운다. 약속(ports/)은 그대로 두므로
 * 화면과 로직은 바꿀 필요가 없다.
 */

import android.content.Context
import com.example.chargesuggest.domain.DriveState
import com.example.chargesuggest.ports.BatterySource
import com.example.chargesuggest.ports.ChargerRoute
import com.example.chargesuggest.ports.DriveStateSource
import com.example.chargesuggest.ports.Speaker

class PleosBatterySource(private val context: Context) : BatterySource {
    // private val vehicle = Vehicle(context)   // TODO(verify): import 경로
    override fun start(onSoc: (Int) -> Unit) {
        // vehicle.initialize()
        // val battery = vehicle.getEvBattery()   // TODO(verify): 메서드 이름
        // battery.check...Capabilities(...)       // TODO(verify)
        // battery.register...(listener)           // TODO(verify): SOC 구독
        TODO("EvBattery SOC 구독 API 확인 후 구현")
    }

    override fun stop() {
        // battery.unregister...(listener)
        // vehicle.release()  // 비동기 콜백이 끝난 뒤에 호출
    }
}

class PleosDriveStateSource(private val context: Context) : DriveStateSource {
    override fun start(onState: (DriveState) -> Unit) {
        TODO("기어/주행 상태 API 확인 후 구현")
    }

    override fun stop() = Unit
}

class PleosSpeaker(private val context: Context) : Speaker {
    override fun speak(text: String) {
        // TextToSpeech(ctx, Mode.ON_DEVICE) → initialize() → addEventListener() → speak()
        TODO("TTS 좌표·권한 확인 후 구현")
    }
}

class PleosChargerRoute(private val context: Context) : ChargerRoute {
    override fun addNearestCharger(onDone: () -> Unit, onFailed: () -> Unit) {
        // NaviHelper: initialize() → addListener() → (충전소 조회) → 경유지 추가 → removeListener() + release()
        TODO("NaviHelper 경유지/충전 사업자 API 확인 후 구현")
    }
}
