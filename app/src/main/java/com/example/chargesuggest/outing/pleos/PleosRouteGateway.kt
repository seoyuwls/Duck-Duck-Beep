package com.example.chargesuggest.outing.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.example.chargesuggest.outing.domain.MAX_STOPS
import com.example.chargesuggest.outing.domain.Stop
import com.example.chargesuggest.outing.ports.CumulativeEta
import com.example.chargesuggest.outing.ports.RouteEvents
import com.example.chargesuggest.outing.ports.RouteGateway

/**
 * NaviHelper(차량 내비 SDK)를 RouteGateway 약속에 맞춘 어댑터.
 * 우리 앱은 길안내를 하지 않고, 목적지·경유지를 넘기고 이벤트만 받는다.
 */
class PleosRouteGateway(private val context: Context) : RouteGateway {

    private var navi: NaviHelper? = null
    @Volatile private var events: RouteEvents? = null

    // 실험에서 확인: 남은 값은 '지금 위치에서 그 지점까지'의 누적값이다
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            events?.onRouteStarted(routeStartInfo.distance, routeStartInfo.duration, routeStartInfo.tollFare)
        }

        override fun onDrivingInfo(info: DrivingInfo) {
            // 경유지(경로 순서) 다음에 도착지
            val list = info.waypoints.map { CumulativeEta(it.name, it.distance, it.duration) } +
                CumulativeEta(info.destination.name, info.destination.distance, info.destination.duration)
            events?.onEtas(list)
        }

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            // 도착지는 들어 있지 않으므로 경유지 값만 전달한다
            events?.onEtas(list.map { CumulativeEta(it.name, it.remainDistance, it.remainTime) })
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            events?.onWaypointArrived(info.name, info.distance, info.duration)
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            events?.onDestinationArrived(info.name, info.distance, info.duration)
        }

        override fun onRouteCancelled() {
            events?.onRouteCancelled()
        }

        override fun onError(errorCode: NaviErrorCode) {
            events?.onError("내비 오류: $errorCode")
        }
    }

    override fun connect(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
        if (navi != null) return onConnected()
        val helper = NaviHelper(context.applicationContext)
        navi = helper
        helper.initialize(
            onConnected = {
                helper.addListener(listener)
                onConnected()
            },
            onFailed = { t ->
                navi = null
                onFailed(t)
            },
        )
    }

    override fun setEvents(events: RouteEvents?) {
        this.events = events
    }

    override fun startRoute(stops: List<Stop>) {
        require(stops.isNotEmpty() && stops.size <= MAX_STOPS) { "방문지는 1~$MAX_STOPS 곳이어야 해요" }
        val helper = navi ?: return events?.onError("내비에 연결되지 않았어요") ?: Unit
        val destination = stops.last().toRouteInfo()
        if (stops.size == 1) {
            helper.requestRoute(destination)
            return
        }
        val waypoints = stops.dropLast(1).mapIndexed { i, s ->
            s.toWaypoint(if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND)
        }
        helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination = destination, waypoints = waypoints))
    }

    override fun refreshEtas() {
        navi?.getWaypointInfo()
    }

    override fun cancelRoute() {
        navi?.cancelRoute()
    }

    override fun release() {
        navi?.removeListener(listener)
        navi?.release()
        navi = null
    }

    // poiId·address·poiSubId는 필수 인자지만 좌표만으로 경로가 되는 것을 실험으로 확인했다
    private fun Stop.toRouteInfo() = RouteInfo(
        latitude = point.lat, longitude = point.lon, poiName = name,
        poiId = "", address = "", poiSubId = "0",
    )

    private fun Stop.toWaypoint(index: WaypointIndex) = RequestWaypointInfo(
        latitude = point.lat, longitude = point.lon, poiName = name,
        waypointIndex = index,
        poiId = "", address = "", poiSubId = "0",
    )
}
