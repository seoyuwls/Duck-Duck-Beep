package com.example.chargesuggest.probe

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SDK 실험 화면. MVP에 필요한 값이 에뮬레이터에서 실제로 오는지 확인한다.
 *  1) 계기판 주행거리(Odometer), 연비(Fuel)
 *  2) 경유지 포함 경로 요청, 경유지 도착 이벤트(구간 거리·시간), 경유지별 남은 시간, 예상 통행료
 *  3) 주의 지점 이벤트
 * 결과는 화면 오른쪽 로그에 시각과 함께 쌓인다.
 */
class ProbeActivity : ComponentActivity() {

    private val logLines = mutableStateListOf<String>()
    private val main = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null

    private fun log(msg: String) {
        // 화면 없이(헤드리스) 실행할 때도 결과를 읽을 수 있게 Logcat에도 남긴다: adb logcat -s PleosProbe
        android.util.Log.d("PleosProbe", msg)
        main.post { logLines.add(0, "${clock.format(Date())}  $msg") }
    }

    // ---- 테스트용 방문지 (수도권 3곳) ----
    // TODO(verify): poiId, poiSubId, address는 필수 인자. 실제 POI 값 없이도 좌표만으로 경로가 되는지 실험으로 확인
    private val stop1 = RequestWaypointInfo(
        latitude = 37.4979, longitude = 127.0276, poiName = "강남역",
        waypointIndex = WaypointIndex.FIRST,
        poiId = "", address = "", poiSubId = "0",
    )
    private val stop2 = RequestWaypointInfo(
        latitude = 37.4021, longitude = 127.1086, poiName = "판교역",
        waypointIndex = WaypointIndex.SECOND,
        poiId = "", address = "", poiSubId = "0",
    )
    private val finalStop = RouteInfo(
        latitude = 37.5446, longitude = 127.0560, poiName = "성수역",
        poiId = "", address = "", poiSubId = "0",
    )

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
            log("경로 시작: ${routeStartInfo.distance}m, ${routeStartInfo.duration}초, 통행료 ${routeStartInfo.tollFare}원")

        override fun onRoutingCompleted() = log("경로 탐색 완료")
        override fun onRouteCancelled() = log("경로 취소됨")
        override fun onWayPointArrived(info: WaypointArrivedInfo) =
            log("경유지 도착: ${info.name} (index=${info.index}) 구간 ${info.distance}m, ${info.duration}초")

        override fun onDestinationArrived(info: DestinationArrivedInfo) =
            log("도착지 도착: ${info.name} 구간 ${info.distance}m, ${info.duration}초")

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            log("경유지 정보 ${list.size}개")
            list.forEach { log("  - ${it.name}: 남은 ${it.remainDistance}m, ${it.remainTime}초") }
        }

        override fun onTripInfo(tripInfo: TripInfo) =
            log("Trip: ${tripInfo.distance}m, ETA ${tripInfo.eta}, 목적지 SOC ${tripInfo.estimatedSocAtDest}")

        override fun onRouteStateInfo(info: RouteStateInfo) =
            log("경로 상태: routing=${info.isRouting}, 옵션=${info.routeOption}, 교통=${info.trafficStatus}")

        override fun onDrivingInfo(info: DrivingInfo) =
            log("주행 정보: 도착지=${info.destination}, 경유지 ${info.waypoints.size}개")

        override fun onCautionPointInfo(list: List<CautionPoint>) =
            log("주의 지점 ${list.size}개: ${list.take(3)}")

        override fun onError(errorCode: NaviErrorCode) = log("내비 오류: $errorCode")
    }

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = log("[구독] 계기판 주행거리 = $distance")
        override fun onFailed(e: Exception) = log("[구독] 실패: ${e.message}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("e2e", false)) main.postDelayed({ runE2e() }, 1500)
        setContent {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SDK 실험", fontSize = 28.sp)
                    Probe("1. 차량 SDK 연결 + 주행거리 구독") { startVehicle() }
                    Probe("2. 계기판 주행거리 / 여정 거리·시간") { readOdometer() }
                    Probe("3. 연비 / 연료 잔량") { readFuel() }
                    Probe("4. 내비 SDK 연결 + 이벤트 구독") { startNavi() }
                    Probe("5. 경로 요청 (경유지 2 + 도착 1)") { requestRoute() }
                    Probe("6. 경유지별 남은 시간 조회") { navi?.getWaypointInfo() ?: log("먼저 4번") }
                    Probe("7. 경로 상태 조회") { navi?.getRouteStateInfo() ?: log("먼저 4번") }
                    Probe("8. Trip 조회 (성수역 ETA)") { requestTrip() }
                    Probe("9. 경로 취소") { navi?.cancelRoute() ?: log("먼저 4번") }
                    Probe("10. 연결 계층 종단 테스트") { runE2e() }
                }
                LazyColumn(Modifier.weight(2f).fillMaxSize()) {
                    items(logLines) { Text(it, fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Probe(label: String, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, fontSize = 20.sp) }
    }

    // ---------- 연결 계층(OutingController + Pleos 어댑터) 종단 테스트 ----------
    private var e2e: com.example.chargesuggest.outing.OutingController? = null
    private var e2eGateway: com.example.chargesuggest.outing.pleos.PleosRouteGateway? = null

    private fun runE2e() {
        if (e2e != null) return log("E2E 이미 실행 중")
        val gw = com.example.chargesuggest.outing.pleos.PleosRouteGateway(applicationContext)
        val odo = com.example.chargesuggest.outing.pleos.PleosOdometerSource(applicationContext)
        val cal = java.util.Calendar.getInstance()
        val c = com.example.chargesuggest.outing.OutingController(
            gw, odo, clockMin = { val k = java.util.Calendar.getInstance(); k.get(java.util.Calendar.HOUR_OF_DAY) * 60 + k.get(java.util.Calendar.MINUTE) },
        )
        e2e = c; e2eGateway = gw
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val stops = listOf(
            com.example.chargesuggest.outing.domain.Stop("a", "강남역", com.example.chargesuggest.outing.domain.Point(37.4979, 127.0276), now + 90),
            com.example.chargesuggest.outing.domain.Stop("b", "판교역", com.example.chargesuggest.outing.domain.Point(37.4021, 127.1086), now + 180),
            com.example.chargesuggest.outing.domain.Stop("c", "성수역", com.example.chargesuggest.outing.domain.Point(37.5446, 127.0560), null),
        )
        log("E2E 시작: 내비 연결 중")
        gw.connect(
            onConnected = {
                log("E2E 내비 연결됨")
                c.attach()
                c.plan(com.example.chargesuggest.outing.domain.Point(37.5665, 126.9780), now, stops, optimize = false)
                log("E2E 계획: ${c.state.value.plan?.order?.map { it.name }} 이동 ${c.state.value.plan?.totalTravelSec}초(추정)")
                c.startNavigation()
                log("E2E 경로 시작 요청")
            },
            onFailed = { t -> log("E2E 내비 연결 실패: ${t.message}") },
        )
        // 5초마다 상태 요약을 남긴다
        val tick = object : Runnable {
            override fun run() {
                val s = c.state.value
                log("E2E 상태: ${s.phase} 내비값수신=${s.navEtaAvailable} 통행료=${s.tollWon} 계기판=${s.odometerKm} 방문기록=${s.visits.size} 오류=${s.lastError} 이동=${s.plan?.totalTravelSec}초")
                main.postDelayed(this, 5000)
            }
        }
        main.postDelayed(tick, 5000)
        main.postDelayed({ gw.refreshEtas() }, 70_000)
    }

    // ---------- Vehicle SDK ----------
    private fun startVehicle() {
        if (vehicle == null) vehicle = Vehicle(applicationContext).also { it.initialize() }
        log("Vehicle 초기화 요청")
        vehicle?.getOdometer()?.registerDistanceDriven(distanceListener)
    }

    private fun readOdometer() {
        val odo = vehicle?.getOdometer() ?: return log("먼저 1번")
        odo.getCurrentDistanceDriven(
            onComplete = { v -> log("계기판 주행거리 = $v") },
            onFailed = { e -> log("주행거리 실패: ${e.message}") },
        )
        odo.getTripCurrentDrivingDistance(
            onComplete = { v -> log("여정 거리 = $v") },
            onFailed = { e -> log("여정 거리 실패: ${e.message}") },
        )
        odo.getTripCurrentDrivingTime(
            onComplete = { v -> log("여정 시간 = $v") },
            onFailed = { e -> log("여정 시간 실패: ${e.message}") },
        )
    }

    private fun readFuel() {
        val fuel = vehicle?.getFuel() ?: return log("먼저 1번")
        fuel.getCumulativeFuelEconomy(
            onComplete = { v -> log("누적 연비 = $v") },
            onFailed = { e -> log("연비 실패: ${e.message}") },
        )
        fuel.getFuelPercent(
            onComplete = { v -> log("연료 잔량(%) = $v") },
            onFailed = { e -> log("연료 잔량 실패: ${e.message}") },
        )
    }

    // ---------- NaviHelper ----------
    private fun startNavi() {
        if (navi != null) return log("이미 연결됨")
        val helper = NaviHelper(applicationContext)
        navi = helper
        helper.initialize(
            onConnected = {
                helper.addListener(naviListener)
                log("내비 연결됨, 이벤트 구독 시작")
            },
            onFailed = { t -> log("내비 연결 실패: ${t.message}") },
        )
    }

    private fun requestRoute() {
        val helper = navi ?: return log("먼저 4번")
        log("경로 요청: ${stop1.poiName} → ${stop2.poiName} → ${finalStop.poiName}")
        helper.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(destination = finalStop, waypoints = listOf(stop1, stop2)),
        )
    }

    private fun requestTrip() {
        val helper = navi ?: return log("먼저 4번")
        helper.requestTrip(
            TripRequest(
                latitude = finalStop.latitude, longitude = finalStop.longitude,
                poiName = "성수역", poiId = "", poiSubId = "0",
            ),
        )
    }

    override fun onDestroy() {
        navi?.removeListener(naviListener)
        navi?.release()
        vehicle?.getOdometer()?.unregisterDistanceDriven(distanceListener)
        vehicle?.release()
        super.onDestroy()
    }
}
