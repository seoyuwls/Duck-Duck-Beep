package com.example.chargesuggest.outing.ports

import com.example.chargesuggest.outing.domain.Stop

/*
 * 외근 비서가 바깥(내비, 차량)에 바라는 일을 적은 약속.
 * 실제 SDK 구현은 outing/pleos/, 테스트용 가짜 구현은 테스트 폴더에 둔다.
 * 로직(outing/OutingController)은 SDK를 직접 모르기 때문에 에뮬레이터 없이 테스트할 수 있다.
 */

/** 현재 위치에서 어떤 지점까지 남은 거리와 시간(누적값) */
data class CumulativeEta(val name: String, val remainDistanceM: Int, val remainSec: Int)

/** 내비가 알려 주는 일 */
interface RouteEvents {
    /** 경로 안내 시작. 값은 첫 구간 기준으로 보인다(실험에서 13km·48분, 통행료 1,400원) */
    fun onRouteStarted(firstLegDistanceM: Int, firstLegDurationSec: Int, tollWon: Int)

    /** 남은 지점들의 누적 도착 예정(경로 순서). 도착지가 빠져 있을 수도 있다 */
    fun onEtas(cumulative: List<CumulativeEta>)

    fun onWaypointArrived(name: String, legDistanceM: Int, legDurationSec: Int)
    fun onDestinationArrived(name: String, legDistanceM: Int, legDurationSec: Int)
    fun onRouteCancelled()
    fun onError(message: String)
}

/** 길안내는 차의 내비가 맡고, 우리 앱은 목적지·경유지만 넘기고 이벤트를 받는다 */
interface RouteGateway {
    fun connect(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun setEvents(events: RouteEvents?)

    /** 방문 순서대로 전달한다. 마지막이 도착지, 앞의 것들이 경유지(최대 2개) */
    fun startRoute(stops: List<Stop>)

    /** 경유지별 남은 시간을 다시 요청한다(결과는 [RouteEvents.onEtas]로 온다) */
    fun refreshEtas()

    fun cancelRoute()
    fun release()
}

/** 계기판 주행거리(km) */
interface OdometerSource {
    fun start(onValue: (Float) -> Unit, onError: (String) -> Unit)
    fun stop()
}
