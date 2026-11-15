package com.example.chargesuggest.outing.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 두 지점 사이의 이동 시간을 구하는 쪽.
 * 방문 순서를 드래그할 때는 내비 요청이 느리므로(에뮬레이터 실험에서 약 1분) 즉시 계산할 수 있는 추정값을 쓰고,
 * 내비가 실제 값을 주면 [CachedEstimator]로 덮어쓴다.
 */
interface TravelEstimator {
    fun estimate(from: Point, to: Point): Leg
}

/** 직선거리에 도로 보정 계수를 곱하고 평균 속도로 시간을 환산하는 단순 추정 */
class HaversineEstimator(
    private val roadFactor: Double = 1.35,
    private val avgSpeedKmh: Double = 30.0,
) : TravelEstimator {
    override fun estimate(from: Point, to: Point): Leg {
        val roadM = (haversineMeters(from, to) * roadFactor).roundToInt()
        val sec = (roadM / 1000.0 / avgSpeedKmh * 3600.0).roundToInt()
        return Leg(roadM, sec)
    }
}

/** 이미 내비에서 받은 실제 구간 값이 있으면 그것을 우선 쓴다 */
class CachedEstimator(
    private val base: TravelEstimator,
    private val known: Map<LegKey, Leg> = emptyMap(),
) : TravelEstimator {
    data class LegKey(val from: Point, val to: Point)

    override fun estimate(from: Point, to: Point): Leg = known[LegKey(from, to)] ?: base.estimate(from, to)
}

/** 교통 상황 슬라이더용: 소요 시간만 배율로 늘리거나 줄인다(거리는 그대로) */
class TrafficAdjustedEstimator(
    private val base: TravelEstimator,
    private val durationMultiplier: Double,
) : TravelEstimator {
    override fun estimate(from: Point, to: Point): Leg {
        val leg = base.estimate(from, to)
        return leg.copy(durationSec = (leg.durationSec * durationMultiplier).roundToInt())
    }
}

fun haversineMeters(a: Point, b: Point): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) +
        cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}
