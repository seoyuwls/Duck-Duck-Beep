package com.example.chargesuggest.duckduck

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.chargesuggest.duck.CarModelHolder
import com.example.chargesuggest.duck.DriveStateSource
import com.example.chargesuggest.duck.SharedPrefsStore
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.pleos.AaosCarInfoSource
import com.example.chargesuggest.duck.pleos.PleosDriveStateSource
import com.example.chargesuggest.duck.pleos.PleosMotionSource
import com.example.chargesuggest.duckduck.physics.DriveMotion
import com.example.chargesuggest.duck.pleos.PleosDuckReactor
import com.example.chargesuggest.duck.pleos.PleosVinSource
import com.example.chargesuggest.duck.domain.ModelSource
import com.example.chargesuggest.duck.domain.MyCarModel
import com.example.chargesuggest.duckduck.domain.Brand
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.NicknameAdjectives
import com.example.chargesuggest.duckduck.domain.Precip
import com.example.chargesuggest.duckduck.domain.WeatherDuckResolver
import com.example.chargesuggest.duckduck.domain.WeatherSnapshot

/** 시연 조작 값 (제출 전에 제거하거나 실제 값으로 바꾼다) */
class DuckDuckDemoState {
    /** true면 실제 차량의 기어·주행 규제로 주행 상태를 정하고, false면 아래 버튼으로 정한다 */
    var driveFromVehicle by mutableStateOf(false)
    var naviSpeaking by mutableStateOf(false)
    var receiverOffline by mutableStateOf(false)
    var controlsOpen by mutableStateOf(true)
    var lastWeatherLabel by mutableStateOf("날씨 없음")
    /** 주행 중 회전·브레이크·요철이 가끔 저절로 일어나게 한다(실제 조향각·브레이크 값은 항상 함께 반영된다) */
    var autoMotion by mutableStateOf(true)
    /** 시연 리모콘: 지금까지 받은 오리 수(중복 없이 차례로 받는다) */
    var demoReceived by mutableStateOf(0)
    /** 시연으로 만든 근처의 같은 차 수 */
    var nearbyCars by mutableStateOf(0)
}

/**
 * Duck Duck 앱의 연결 담당. 컨트롤러를 만들고 실제 차량 연결(주행 상태, 차종, 앰비언트·효과음)을 끼운다.
 * 서버가 없어서 근처 차와 받는 오리는 목업(MockDuckDuckNetwork)과 시연 버튼으로 만든다.
 * 화면 회전으로 Activity가 다시 만들어져도 상태가 사라지지 않게 ViewModel에 둔다.
 */
class DuckDuckViewModel(app: Application) : AndroidViewModel(app) {

    val demo = DuckDuckDemoState()

    private val prefs = SharedPrefsStore(app)
    private val network = MockDuckDuckNetwork()
    private val pleos: PleosDuckReactor? = runCatching { PleosDuckReactor(app) }.getOrNull()
    private val driveSource: DriveStateSource = PleosDriveStateSource(app)
    private var vehicleMode: DriveMode? = null

    /** 오리 통이 받는 차의 움직임. 실제 조향각·브레이크가 들어오고, 시연 버튼으로도 일으킬 수 있다 */
    val motion = DriveMotion()
    private val motionSource = PleosMotionSource(app, motion)
    private var carSeq = 0
    private val demoCars = ArrayDeque<String>()

    /** 내 차종 (차량 정보 + 차대번호 + 설정에서 직접 선택) */
    val carModel = CarModelHolder(PleosVinSource(app), prefs, nameSource = AaosCarInfoSource(app))

    /** 차종을 인식하지 못했을 때(에뮬레이터)만 시연용 차종(쏘렌토)을 쓴다. 인식된 값이 있으면 그대로 쓴다 */
    private fun demoCar(model: MyCarModel): MyCarModel = when {
        DuckConfig.DEMO_CAR_MODEL_LABEL.isEmpty() -> model
        model.source == ModelSource.MANUAL || model.source == ModelSource.SYSTEM || model.source == ModelSource.VIN_TABLE -> model
        else -> MyCarModel(DuckConfig.DEMO_CAR_MODEL_KEY, DuckConfig.DEMO_CAR_MODEL_LABEL, ModelSource.MANUAL)
    }

    val controller = DuckDuckController(
        network = network,
        reactor = object : com.example.chargesuggest.duck.DuckReactor {
            // 차량 연결이 실패해도 앱이 멈추지 않게 감싼다
            override fun react(plan: com.example.chargesuggest.duck.domain.ReactionPlan) {
                runCatching { pleos?.react(plan) }
            }
        },
        persistence = DuckDuckPersistence(prefs),
        myCar = { demoCar(carModel.model.value) },
        myBrand = { Brand.fromMake(carModel.manufacturer) },
        naviSpeaking = { demo.naviSpeaking },
    )

    init {
        controller.attach()
        runCatching { pleos?.warmUp() } // 차량 연결 첫 호출이 약 4초 걸려서 미리 해 둔다
        carModel.start()
        motionSource.start()
        controller.setDriveMode(DriveMode.PARKED)
        runCatching {
            driveSource.start { mode ->
                vehicleMode = mode
                if (demo.driveFromVehicle) controller.setDriveMode(mode)
            }
        }
    }

    // ---- 시연 조작 ----

    fun useVehicleDriveState(on: Boolean) {
        demo.driveFromVehicle = on
        if (on) vehicleMode?.let(controller::setDriveMode)
    }

    /** 주행 상태를 직접 정한다 (차량 연동은 꺼진다) */
    fun setMode(mode: DriveMode) {
        demo.driveFromVehicle = false
        controller.setDriveMode(mode)
    }

    /** 근처에서 같은 차종이 나타난 것으로 한다 (시스템은 알리지 않는다. 운전자가 오리를 던졌을 때만 쓰인다) */
    fun simulateFound() {
        val id = "car-${++carSeq}"
        demoCars += id
        network.simulateFound(id)
        demo.nearbyCars = demoCars.size
    }

    /** 근처의 같은 차가 모두 멀어진 것으로 한다 */
    fun simulateAllLost() {
        while (demoCars.isNotEmpty()) network.simulateLost(demoCars.removeFirst())
        demo.nearbyCars = 0
    }

    /**
     * 상대 차가 오리를 보낸 것으로 한다. 상대 쪽 날씨는 알 수 없으니 종류는 무작위로 고른다
     * (기본 오리가 가장 자주 나오고, 무지개 오리는 아주 드물다). 시연에서 여러 종류를 보여 주려는 것이다.
     */
    fun simulateReceived() {
        val weights = listOf(
            DuckType.BASIC to 4, DuckType.UMBRELLA to 2, DuckType.RAINCOAT to 2, DuckType.SNOWMAN to 2,
            DuckType.SUNGLASSES to 2, DuckType.SCARF to 2, DuckType.RAINBOW to 1,
        )
        var roll = kotlin.random.Random.nextInt(weights.sumOf { it.second })
        val type = weights.first { roll -= it.second; roll < 0 }.first
        val from = "${NicknameAdjectives.randomDefault()} ${controller.state.value.modelName}"
        network.simulateReceived(type, from)
    }

    /** 시연에서 받는 오리의 종류를 직접 정해서 받는다 */
    fun simulateReceived(type: DuckType) {
        val from = "${NicknameAdjectives.randomDefault()} ${controller.state.value.modelName}"
        network.simulateReceived(type, from)
    }

    fun setReceiverOffline(on: Boolean) {
        demo.receiverOffline = on
        network.online = !on
    }

    /** 시연용 날씨 주입 */
    fun injectWeather(preset: WeatherPreset) {
        demo.lastWeatherLabel = preset.label
        val now = System.currentTimeMillis()
        controller.injectWeather(preset.snapshot(now), now)
    }

    fun resetSaved() { simulateAllLost(); demo.demoReceived = 0; controller.resetSaved() }

    // ---- 시연 리모콘(주행 시작 / 오리 보내기 / 오리 받기 / 정차) ----

    /** 시연용으로 차례로 받는 오리. 종류가 겹치지 않게 돌아가며 보여 준다(기본 오리는 이미 통에 있어서 뺐다) */
    private val demoReceiveOrder = listOf(
        DuckType.UMBRELLA, DuckType.SNOWMAN, DuckType.SUNGLASSES, DuckType.RAINCOAT, DuckType.SCARF, DuckType.RAINBOW,
    )

    fun demoStartDriving() {
        setAutoMotion(true)
        setMode(DriveMode.DRIVING)
        motion.startSpeedBump(3f) // 출발하고 3초쯤 뒤에 방지턱을 넘는 모션
    }

    fun demoStop() = setMode(DriveMode.STOPPED)

    /**
     * 시연용: 근처에 같은 차가 있는 상태를 켜고 끈다. 시스템은 알리지 않고(화면 변화 없음), 이 상태에서 오리를 던져야 전달된다.
     * 켜져 있으면 true.
     */
    fun demoToggleNearby() {
        if (demoCars.isEmpty()) simulateFound() else simulateAllLost()
    }

    /** 시연용: 다음 오리를 받는다. 같은 종류가 연달아 나오지 않게 정해진 순서로 돌아간다 */
    fun demoReceive() {
        val type = demoReceiveOrder[demo.demoReceived % demoReceiveOrder.size]
        demo.demoReceived++
        simulateReceived(type)
    }

    /** 시연: 확률과 하루 한 번 제한 없이 지금 날씨의 오리를 선물로 받는다 */
    fun grantWeatherDuck() { controller.grantWeatherDuckNow() }

    /** 시연: 주행 중 차의 움직임을 직접 일으킨다 */
    fun driveEvent(e: DriveMotion.Event) = motion.trigger(e)

    fun setAutoMotion(on: Boolean) {
        demo.autoMotion = on
        motion.auto = on
    }

    override fun onCleared() {
        runCatching { driveSource.stop() }
        runCatching { motionSource.stop() }
        runCatching { pleos?.release() }
    }
}

/** 시연에서 고르는 날씨 7가지 (명세 7.1 "시연용 주입 모드") */
enum class WeatherPreset(val label: String) {
    CLEAR("맑음"), LIGHT_RAIN("약한 비"), HEAVY_RAIN("강한 비"), SNOW("눈"),
    HOT("폭염"), COLD("한파"), RAIN_STOPPED("비 그침");

    fun snapshot(nowMs: Long): WeatherSnapshot = when (this) {
        CLEAR -> WeatherSnapshot(Precip.NONE, 20.0)
        LIGHT_RAIN -> WeatherSnapshot(Precip.LIGHT_RAIN, 18.0)
        HEAVY_RAIN -> WeatherSnapshot(Precip.HEAVY_RAIN, 18.0)
        SNOW -> WeatherSnapshot(Precip.SNOW, -2.0)
        HOT -> WeatherSnapshot(Precip.NONE, 35.0)
        COLD -> WeatherSnapshot(Precip.NONE, -5.0)
        RAIN_STOPPED -> WeatherSnapshot(Precip.NONE, 18.0, rainEndedAtMs = nowMs - 10 * 60_000L)
    }
}
