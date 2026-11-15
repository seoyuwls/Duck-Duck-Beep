package com.example.chargesuggest.outing.domain

import kotlin.math.ceil

/** 지연 때문에 순서를 바꾸자고 제안할 때 쓰는 결과 */
data class Replan(
    val current: Plan,
    val suggested: Plan,
    /** 지각이 줄어든 분 */
    val savedLateMin: Int,
    /** 지각하는 방문 수가 줄어든 만큼 */
    val savedLateCount: Int,
)

/**
 * 외근 일정 계산. 방문지는 최대 [MAX_STOPS]곳이라 모든 순서를 직접 비교해도 6가지 이하다.
 *
 * 좋은 순서의 기준(앞이 우선): 지각하는 방문 수 → 지각한 분의 합 → 빠듯한 방문 수 → 총 이동 시간
 */
class OutingPlanner(
    private val estimator: TravelEstimator,
    /** 약속 시각까지 이만큼 못 남으면 "빠듯함" */
    private val tightBufferMin: Int = 10,
) {
    /** 주어진 순서대로 갔을 때의 도착 예정과 여유 시간을 계산한다 */
    fun evaluate(start: Point, departMin: Int, order: List<Stop>): Plan {
        require(order.size <= MAX_STOPS) { "방문지는 최대 ${MAX_STOPS}곳이에요" }
        var pos = start
        var time = departMin
        var distance = 0
        var travelSec = 0
        val arrivals = ArrayList<Arrival>(order.size)

        for (stop in order) {
            val leg = estimator.estimate(pos, stop.point)
            val arrive = time + ceil(leg.durationSec / 60.0).toInt()
            val appointment = stop.appointmentMin
            val slack = appointment?.minus(arrive)
            val begin = if (appointment != null && arrive < appointment) appointment else arrive
            val status = when {
                slack == null -> ArrivalStatus.OK
                slack < 0 -> ArrivalStatus.LATE
                slack < tightBufferMin -> ArrivalStatus.TIGHT
                else -> ArrivalStatus.OK
            }
            val depart = begin + stop.stayMin
            arrivals += Arrival(stop, arrive, begin, depart, slack, status, leg)
            distance += leg.distanceM
            travelSec += leg.durationSec
            pos = stop.point
            time = depart
        }
        return Plan(arrivals, distance, travelSec)
    }

    /** 모든 순서를 비교해 가장 좋은 계획을 고른다. 같으면 입력 순서를 우선한다 */
    fun optimize(start: Point, departMin: Int, stops: List<Stop>): Plan {
        require(stops.size <= MAX_STOPS) { "방문지는 최대 ${MAX_STOPS}곳이에요" }
        var best: Plan? = null
        for (order in permutations(stops)) {
            val plan = evaluate(start, departMin, order)
            if (best == null || isBetter(plan, best)) best = plan
        }
        return best ?: evaluate(start, departMin, emptyList())
    }

    /**
     * 이동 중 지연이 생겼을 때 남은 방문지의 순서를 바꾸면 나아지는지 본다.
     * 뚜렷하게 좋아질 때만 제안하고, 아니면 null.
     */
    fun suggestReplan(
        nowPoint: Point,
        nowMin: Int,
        remainingInCurrentOrder: List<Stop>,
        minGainMin: Int = 5,
    ): Replan? {
        if (remainingInCurrentOrder.size < 2) return null
        val current = evaluate(nowPoint, nowMin, remainingInCurrentOrder)
        val suggested = optimize(nowPoint, nowMin, remainingInCurrentOrder)
        if (suggested.order == current.order) return null
        val savedCount = current.lateCount - suggested.lateCount
        val savedMin = current.totalLateMin - suggested.totalLateMin
        val clearlyBetter = savedCount > 0 || (savedCount == 0 && savedMin >= minGainMin)
        return if (clearlyBetter) Replan(current, suggested, savedMin, savedCount) else null
    }

    private fun isBetter(a: Plan, b: Plan): Boolean {
        if (a.lateCount != b.lateCount) return a.lateCount < b.lateCount
        if (a.totalLateMin != b.totalLateMin) return a.totalLateMin < b.totalLateMin
        if (a.tightCount != b.tightCount) return a.tightCount < b.tightCount
        return a.totalTravelSec < b.totalTravelSec
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val result = ArrayList<List<T>>()
        for (i in items.indices) {
            val rest = items.filterIndexed { index, _ -> index != i }
            for (tail in permutations(rest)) result += listOf(items[i]) + tail
        }
        return result
    }
}
