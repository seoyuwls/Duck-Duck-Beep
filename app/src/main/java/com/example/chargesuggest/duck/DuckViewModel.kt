package com.example.chargesuggest.duck

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.nearby.PresenceReporter
import com.example.chargesuggest.duck.nearby.SendResult
import com.example.chargesuggest.duck.nearby.SimulatedNearbyNetwork
import com.example.chargesuggest.duck.pleos.AaosCarInfoSource
import com.example.chargesuggest.duck.pleos.PleosDriveStateSource
import com.example.chargesuggest.duck.pleos.PleosVinSource
import com.example.chargesuggest.duck.pleos.PleosDuckReactor
import com.example.chargesuggest.ui.duck.DemoReactor
import com.example.chargesuggest.ui.duck.DemoRoller
import com.example.chargesuggest.ui.duck.DemoState
import com.example.chargesuggest.ui.duck.Viewpoint

/**
 * 시연용으로 차 두 대(보내는 차, 받는 차)를 메모리에서 잇는다. 화면에는 한 번에 한 대만 보인다.
 * 화면 회전 등으로 Activity가 다시 만들어져도 도감이 사라지지 않도록 ViewModel에 둔다.
 *
 * 시연 흐름: 보내는 차에서 전달하기 -> 잠시 "전달했어요"를 보여 준 뒤 -> 받는 차 화면으로 넘어가
 * 수령 장면(카드, 조명, 효과음)을 보여 준다.
 *
 * 실제 조명·효과음은 받는 차(받는 쪽)에서만 낸다. 한 대의 차 안에서 두 대분이 겹치면
 * 조명 색이 서로 덮어쓰기 때문이다. 보내는 차 쪽은 화면 표시만 한다.
 */
class DuckViewModel(app: Application) : AndroidViewModel(app) {

    val demo = DemoState()
    /** 앱을 껐다 켜도 남는 값(도감, 닉네임, 설정, 직접 고른 차종)의 저장소 */
    private val prefs = SharedPrefsStore(app)
    private val link = DuckLink()
    private val main = Handler(Looper.getMainLooper())
    private var carSeq = 1

    private val pleos: PleosDuckReactor? = runCatching { PleosDuckReactor(app) }.getOrNull()
    private val driveSource: DriveStateSource = PleosDriveStateSource(app)

    /** 근처 인식 시뮬레이션 (서버 대신 가짜 차 두 대와 SimServer). 실제 서버를 만들면 이 자리에 서버 연결을 끼운다 */
    private val simNetwork = SimulatedNearbyNetwork()
    private val reporter = PresenceReporter(simNetwork.myToken) { simNetwork.report(it) }

    /** 내 차종 (차대번호로 인식 + 설정에서 직접 선택). 시연에서 두 차는 같은 차량이라 하나를 같이 쓴다 */
    val carModel = CarModelHolder(PleosVinSource(app), prefs, nameSource = AaosCarInfoSource(app))

    val meReactor = DemoReactor(delegate = null)
    val otherReactor = DemoReactor(delegate = pleos)

    private val roller = DemoRoller(demo)

    /** 전달하면 곧바로 보내지 않고, 보내는 차 화면을 잠시 보여 준 뒤 받는 차 시점으로 넘어가서 전달한다 */
    private val meNetwork = object : DuckNetwork {
        override fun setReceiver(onReceived: (com.example.chargesuggest.duck.domain.ReceivedDuck) -> Unit) =
            link.a.setReceiver(onReceived)

        override fun setNearbyListener(listener: NearbyListener) = simNetwork.setNearbyListener(listener)

        override fun send(targetId: String, fromNickname: String, kind: DuckKind) {
            // 시뮬레이션 중이면 서버가 먼저 검사한다(근처인지, 닉네임, 횟수). 통과해야 받는 차로 전달된다
            if (demo.nearbySim && simNetwork.trySend(targetId, fromNickname, kind) != SendResult.OK) return
            main.postDelayed({
                demo.viewpoint = Viewpoint.RECEIVER
                link.a.send(targetId, fromNickname, kind)
                android.util.Log.d("PonyDemo", "받는 차 시점으로 전환하고 전달함: ${kind.name}")
            }, SWITCH_DELAY_MS)
        }
    }

    val me = DuckController(
        meNetwork, meReactor, roller = roller,
        vehicleContext = { demo.contexts() },
        naviSpeaking = { demo.naviSpeaking },
        persistence = DuckPersistence(prefs, "me"),
    )
    val other = DuckController(
        link.b, otherReactor, roller = roller,
        naviSpeaking = { demo.naviSpeaking },
        appVisible = { !demo.receiverAppHidden },
        persistence = DuckPersistence(prefs, "other"),
    )

    init {
        me.attach(); other.attach()
        // 시연이 바로 시작되도록 기본값을 정해 둔다 (둘 다 주행 중, 보내는 차가 같은 차를 발견한 상태)
        // 저장된 닉네임이 없을 때만 기본 닉네임을 쓴다
        if (me.state.value.nickname.isBlank()) me.setNickname("히힝이")
        if (other.state.value.nickname.isBlank()) other.setNickname("도로위포니")
        me.setDriveMode(DriveMode.DRIVING); other.setDriveMode(DriveMode.DRIVING)
        me.setNearby(NearbyCar("car-$carSeq"))
        // 차량 연결은 처음에 약 4초 걸리므로 미리 해 둔다
        runCatching { pleos?.warmUp() }
        carModel.start()
        // 실제 차량의 주행 상태(기어 + 주행 규제)를 받는다. 값이 오면 두 차 모두에 반영한다
        // (시연에서 보내는 차와 받는 차는 같은 차량이므로 같은 주행 상태를 쓴다)
        runCatching {
            driveSource.start { mode ->
                demo.vehicleMode = mode
                if (demo.driveFromVehicle) applyDriveMode(mode)
            }
        }
    }

    // ---- 시연 조작 ----

    /** 보내는 차 시점으로 돌아간다. 같은 상대에게는 하루에 한 번만 보낼 수 있어서 새 상대를 발견한 것으로 한다 */
    fun showSender() {
        demo.viewpoint = Viewpoint.SENDER
        if (demo.nearbyFound) me.setNearby(NearbyCar("car-${++carSeq}"))
    }

    fun showReceiver() { demo.viewpoint = Viewpoint.RECEIVER }

    /** 근처 인식을 시뮬레이션으로 할지(true) 버튼으로 할지(false) */
    fun setNearbySim(on: Boolean) {
        if (demo.nearbySim == on) return
        demo.nearbySim = on
        if (on) {
            me.setNearby(null)
            reporter.enabled = true
            simNetwork.start(carModel.model.value.key)
            main.post(ticker)
        } else {
            main.removeCallbacks(ticker)
            reporter.enabled = false
            simNetwork.stop()
            demo.simStatus = ""
            me.setNearby(if (demo.nearbyFound) NearbyCar("car-${++carSeq}") else null)
        }
    }

    /** 가짜 차들을 처음 위치에서 다시 출발시킨다 */
    fun restartNearbySim() {
        if (!demo.nearbySim) return
        me.setNearby(null)
        simNetwork.start(carModel.model.value.key)
    }

    /** 1초마다: 내 위치·차종·주행 상태를 알리고 가짜 차를 움직인다 */
    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val key = carModel.model.value.key
            reporter.modelKey = key
            reporter.driveMode = me.state.value.driveMode
            reporter.onLocation(simNetwork.userLat, simNetwork.userLon)
            simNetwork.syncModel(key)
            reporter.tick(now)
            simNetwork.tick(now)
            demo.simStatus = if (key == null) "차종을 알아야 근처를 찾을 수 있어요 (설정에서 선택)" else simNetwork.status()
            main.postDelayed(this, TICK_MS)
        }
    }

    fun setNearbyFound(on: Boolean) {
        if (demo.nearbySim) return   // 시뮬레이션 중에는 자동으로 정해진다
        demo.nearbyFound = on
        me.setNearby(if (on) NearbyCar("car-${++carSeq}") else null)
    }

    private fun applyDriveMode(mode: DriveMode) { me.setDriveMode(mode); other.setDriveMode(mode) }

    /** 두 차의 도감과 보낸 개수를 비운다 (시연을 처음부터 다시 할 때) */
    fun resetData() { me.clearCollection(); other.clearCollection() }

    /** 주행 상태를 실제 차량에서 받을지(true) 수동 버튼으로 정할지(false) */
    fun useVehicleDriveState(on: Boolean) {
        demo.driveFromVehicle = on
        if (on) demo.vehicleMode?.let(::applyDriveMode)
    }

    /** 지금 화면에 보이는 차의 주행 상태를 수동으로 바꾼다 (차량 연동은 꺼진다) */
    fun setCurrentDriveMode(mode: DriveMode) {
        demo.driveFromVehicle = false
        if (demo.viewpoint == Viewpoint.RECEIVER) other.setDriveMode(mode) else me.setDriveMode(mode)
    }

    override fun onCleared() {
        main.removeCallbacksAndMessages(null)
        runCatching { driveSource.stop() }
        runCatching { pleos?.release() }
    }

    private companion object {
        /** "전달했어요"를 보여 주고 받는 차로 넘어가기까지의 시간 */
        const val SWITCH_DELAY_MS = 1_500L
        const val TICK_MS = 1_000L
    }
}
