package com.example.chargesuggest.outing

import com.example.chargesuggest.outing.domain.Briefing
import com.example.chargesuggest.outing.domain.BriefingComposer
import com.example.chargesuggest.outing.domain.CachedEstimator
import com.example.chargesuggest.outing.domain.HaversineEstimator
import com.example.chargesuggest.outing.domain.Leg
import com.example.chargesuggest.outing.domain.OutingPlanner
import com.example.chargesuggest.outing.domain.Plan
import com.example.chargesuggest.outing.domain.Point
import com.example.chargesuggest.outing.domain.Replan
import com.example.chargesuggest.outing.domain.Stop
import com.example.chargesuggest.outing.domain.TrafficAdjustedEstimator
import com.example.chargesuggest.outing.domain.TravelEstimator
import com.example.chargesuggest.outing.domain.TripRecorder
import com.example.chargesuggest.outing.domain.TripSummary
import com.example.chargesuggest.outing.domain.VisitInfo
import com.example.chargesuggest.outing.domain.VisitRecord
import com.example.chargesuggest.outing.ports.CumulativeEta
import com.example.chargesuggest.outing.ports.OdometerSource
import com.example.chargesuggest.outing.ports.RouteEvents
import com.example.chargesuggest.outing.ports.RouteGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, PLANNED, NAVIGATING, DONE }

/** 화면이 그릴 모든 것. 화면은 이 값만 보고 그린다 */
data class OutingState(
    val phase: Phase = Phase.IDLE,
    /** 출발 시점 기준 전체 계획(출발 전 타임라인) */
    val plan: Plan? = null,
    /** 이동 중에는 지금 위치와 시각 기준으로 다시 계산한 남은 계획 */
    val remainingPlan: Plan? = null,
    /** 지연 때문에 순서를 바꾸면 나아질 때의 제안 */
    val replan: Replan? = null,
    val visits: List<VisitRecord> = emptyList(),
    val nextStopIndex: Int = 0,
    val tollWon: Int? = null,
    val odometerKm: Float? = null,
    val trafficMultiplier: Double = 1.0,
    /** 내비가 준 실제 구간 값을 한 번이라도 받았는지 */
    val navEtaAvailable: Boolean = false,
    val lastError: String? = null,
)

/**
 * 외근 비서의 조립부. 내비 이벤트와 계기판 값을 받아 계획·기록·제안을 갱신한다.
 *
 * - 방문 순서를 바꿀 때는 즉시 계산(추정)하고, 내비가 실제 구간 값을 주면 그 값으로 바꿔 끼운다.
 * - 이벤트는 SDK 스레드에서 오므로 상태 변경은 모두 같은 잠금 안에서 한다.
 */
class OutingController(
    private val gateway: RouteGateway,
    private val odometer: OdometerSource,
    private val baseEstimator: TravelEstimator = HaversineEstimator(),
    /** 지금 시각(자정 기준 분) */
    private val clockMin: () -> Int,
) : RouteEvents {

    private val lock = Any()
    private val knownLegs = HashMap<CachedEstimator.LegKey, Leg>()
    private var multiplier = 1.0
    private var startPoint: Point? = null
    private var departMin = 0
    private var order: List<Stop> = emptyList()
    private var recorder = TripRecorder()
    private var lastOdometerKm: Float? = null

    private val _state = MutableStateFlow(OutingState())
    val state: StateFlow<OutingState> = _state.asStateFlow()

    // ---------- 수명 ----------
    fun attach() {
        gateway.setEvents(this)
        odometer.start(
            onValue = { km ->
                synchronized(lock) {
                    lastOdometerKm = km
                    _state.value = _state.value.copy(odometerKm = km)
                }
            },
            onError = { msg -> synchronized(lock) { _state.value = _state.value.copy(lastError = msg) } },
        )
    }

    fun detach() {
        odometer.stop()
        gateway.setEvents(null)
    }

    // ---------- 출발 전 ----------
    /** 방문지로 계획을 만든다. [optimize]가 true면 가장 좋은 순서를 고른다 */
    fun plan(start: Point, departMin: Int, stops: List<Stop>, optimize: Boolean = true) {
        synchronized(lock) {
            this.startPoint = start
            this.departMin = departMin
            val p = planner()
            order = if (optimize) p.optimize(start, departMin, stops).order else stops
            _state.value = recompute(OutingState(phase = Phase.PLANNED, odometerKm = lastOdometerKm))
        }
    }

    /** 드래그 등으로 순서를 바꿨을 때. 즉시 추정값으로 다시 계산한다 */
    fun reorder(newOrder: List<Stop>) {
        synchronized(lock) {
            require(_state.value.phase == Phase.PLANNED) { "출발 전에만 순서를 바꿀 수 있어요" }
            order = newOrder
            _state.value = recompute(_state.value.copy(replan = null))
        }
    }

    /** 교통 상황 슬라이더(1.0 = 평소, 2.0 = 두 배 걸림) */
    fun setTrafficMultiplier(value: Double) {
        synchronized(lock) {
            multiplier = value
            _state.value = recompute(_state.value.copy(trafficMultiplier = value))
        }
    }

    // ---------- 이동 ----------
    /** "경로 시작": 내비에 목적지와 경유지를 넘기고 기록을 시작한다 */
    fun startNavigation() {
        synchronized(lock) {
            require(order.isNotEmpty()) { "방문지가 없어요" }
            recorder = TripRecorder().also { it.start(lastOdometerKm, clockMin()) }
            _state.value = recompute(_state.value.copy(phase = Phase.NAVIGATING, replan = null, lastError = null, visits = emptyList()))
            gateway.startRoute(order)
        }
    }

    /** 제안받은 순서로 남은 방문지를 다시 내비에 넘긴다 */
    fun applyReplan() {
        synchronized(lock) {
            val replan = _state.value.replan ?: return
            val arrived = recorder.visits().size
            order = order.take(arrived) + replan.suggested.order
            _state.value = recompute(_state.value.copy(replan = null))
            gateway.startRoute(replan.suggested.order)
        }
    }

    /** 방문지 도착 후 사용자가 한 번 탭으로 고른 방문 목적 */
    fun setPurpose(index: Int, purpose: String) {
        synchronized(lock) {
            recorder.setPurpose(index, purpose)
            _state.value = _state.value.copy(visits = recorder.visits())
        }
    }

    /** 다음 방문지의 음성 브리핑(도착 예정까지 남은 시간에 맞춰 길이를 조절) */
    fun briefing(stop: Stop, info: VisitInfo): Briefing? = synchronized(lock) {
        val plan = _state.value.remainingPlan ?: _state.value.plan ?: return null
        val arrival = plan.arrivals.firstOrNull { it.stop.id == stop.id } ?: return null
        val minutes = (arrival.arriveMin - clockMin()).coerceAtLeast(1)
        BriefingComposer().compose(stop, info, minutes)
    }

    fun summary(): TripSummary = synchronized(lock) { recorder.summary() }

    // ---------- 내비 이벤트 ----------
    override fun onRouteStarted(firstLegDistanceM: Int, firstLegDurationSec: Int, tollWon: Int) {
        synchronized(lock) { _state.value = _state.value.copy(tollWon = tollWon) }
    }

    override fun onEtas(cumulative: List<CumulativeEta>) {
        synchronized(lock) {
            val start = startPoint ?: return
            val arrived = recorder.visits().size
            val rest = order.drop(arrived)
            var prevPoint = if (arrived == 0) start else order[arrived - 1].point
            var prevDistance = 0
            var prevSec = 0
            // 누적값의 차이가 방문지 사이의 실제 구간이다(경로 순서와 남은 방문지가 1:1)
            for ((eta, stop) in cumulative.zip(rest)) {
                knownLegs[CachedEstimator.LegKey(prevPoint, stop.point)] = Leg(
                    distanceM = (eta.remainDistanceM - prevDistance).coerceAtLeast(0),
                    durationSec = (eta.remainSec - prevSec).coerceAtLeast(0),
                )
                prevPoint = stop.point
                prevDistance = eta.remainDistanceM
                prevSec = eta.remainSec
            }
            _state.value = recompute(_state.value).copy(navEtaAvailable = true)
        }
    }

    override fun onWaypointArrived(name: String, legDistanceM: Int, legDurationSec: Int) = onArrived(name, legDistanceM, false)

    override fun onDestinationArrived(name: String, legDistanceM: Int, legDurationSec: Int) = onArrived(name, legDistanceM, true)

    private fun onArrived(name: String, navDistanceM: Int, isDestination: Boolean) {
        synchronized(lock) {
            recorder.onArrive(name, lastOdometerKm, navDistanceM.takeIf { it > 0 }, clockMin())
            val phase = if (isDestination) Phase.DONE else _state.value.phase
            _state.value = recompute(_state.value.copy(phase = phase))
        }
    }

    override fun onRouteCancelled() {
        synchronized(lock) { _state.value = _state.value.copy(phase = Phase.PLANNED, replan = null) }
    }

    override fun onError(message: String) {
        synchronized(lock) { _state.value = _state.value.copy(lastError = message) }
    }

    // ---------- 계산 ----------
    private fun planner() = OutingPlanner(
        TrafficAdjustedEstimator(CachedEstimator(baseEstimator, HashMap(knownLegs)), multiplier),
    )

    private fun currentPoint(arrived: Int): Point? = if (arrived == 0) startPoint else order[arrived - 1].point

    /** 순서, 실제 구간 값, 교통 배율이 바뀔 때마다 전체 계획·남은 계획·순서 제안을 다시 계산한다 */
    private fun recompute(current: OutingState): OutingState {
        val start = startPoint ?: return current
        val p = planner()
        val plan = p.evaluate(start, departMin, order)
        val arrived = recorder.visits().size
        var remaining: Plan? = null
        var replan: Replan? = null
        if (current.phase == Phase.NAVIGATING) {
            val here = currentPoint(arrived) ?: start
            val rest = order.drop(arrived)
            remaining = p.evaluate(here, clockMin(), rest)
            replan = p.suggestReplan(here, clockMin(), rest)
        }
        return current.copy(
            plan = plan,
            remainingPlan = remaining,
            replan = replan,
            visits = recorder.visits(),
            nextStopIndex = arrived,
        )
    }
}
