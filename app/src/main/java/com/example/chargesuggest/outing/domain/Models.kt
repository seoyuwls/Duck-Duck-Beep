package com.example.chargesuggest.outing.domain

/**
 * 외근 비서의 핵심 모델. SDK에 의존하지 않는 순수 Kotlin이라 에뮬레이터 없이 테스트할 수 있다.
 * 시각은 "자정 기준 분"(예: 오후 2시 = 840)으로 다룬다.
 */

/** 내비 SDK 제한: 경유지 2개 + 도착지 1개 = 방문지 최대 3곳 */
const val MAX_STOPS = 3

data class Point(val lat: Double, val lon: Double)

data class Stop(
    val id: String,
    val name: String,
    val point: Point,
    /** 약속 시각(자정 기준 분). null이면 시간 제약이 없는 방문 */
    val appointmentMin: Int? = null,
    /** 방문지에 머무는 시간(분) */
    val stayMin: Int = 30,
)

/** 이동 구간의 거리와 소요 시간(추정값 또는 내비 실측값) */
data class Leg(val distanceM: Int, val durationSec: Int)

enum class ArrivalStatus {
    /** 여유 있음(또는 약속 시각이 없음) */
    OK,

    /** 약속 시각 전에 도착하지만 여유가 적음 */
    TIGHT,

    /** 약속 시각을 넘겨 도착 */
    LATE,
}

data class Arrival(
    val stop: Stop,
    val arriveMin: Int,
    /** 실제 시작 시각. 일찍 도착하면 약속 시각까지 기다린다 */
    val startMin: Int,
    val departMin: Int,
    /** 약속 시각 - 도착 시각(분). 음수면 지각. 약속이 없으면 null */
    val slackMin: Int?,
    val status: ArrivalStatus,
    val leg: Leg,
)

data class Plan(
    val arrivals: List<Arrival>,
    val totalDistanceM: Int,
    val totalTravelSec: Int,
) {
    val order: List<Stop> get() = arrivals.map { it.stop }
    val lateCount: Int get() = arrivals.count { it.status == ArrivalStatus.LATE }
    val tightCount: Int get() = arrivals.count { it.status == ArrivalStatus.TIGHT }

    /** 지각한 분의 합 */
    val totalLateMin: Int
        get() = arrivals.sumOf { a ->
            val slack = a.slackMin
            if (slack != null && slack < 0) -slack else 0
        }
}

/** 시각 표시 도우미: 840 -> "오후 2시", 870 -> "오후 2시 30분" */
fun formatTime(minOfDay: Int): String {
    val m = ((minOfDay % 1440) + 1440) % 1440
    val h = m / 60
    val mm = m % 60
    val period = if (h < 12) "오전" else "오후"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return if (mm == 0) "$period ${h12}시" else "$period ${h12}시 ${mm}분"
}

/** 시와 분을 자정 기준 분으로 */
fun hm(hour: Int, minute: Int = 0): Int = hour * 60 + minute
