package com.example.chargesuggest.outing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 테스트용 이동 추정: 위도를 "km 좌표"로 쓰고 1km에 1분(시속 60km)이 걸린다고 본다 */
private class LineEstimator : TravelEstimator {
    override fun estimate(from: Point, to: Point): Leg {
        val km = abs(to.lat - from.lat)
        return Leg(distanceM = (km * 1000).toInt(), durationSec = (km * 60).toInt())
    }
}

private fun at(km: Double) = Point(km, 0.0)

class OutingPlannerTest {

    private val planner = OutingPlanner(LineEstimator())
    private val start = at(0.0)
    private val depart = hm(9, 0)

    // A: 10km, 12시 약속 / B: 20km, 10시 약속 / C: 5km, 약속 없음
    private val a = Stop("a", "A", at(10.0), appointmentMin = hm(12, 0), stayMin = 30)
    private val b = Stop("b", "B", at(20.0), appointmentMin = hm(10, 0), stayMin = 30)
    private val c = Stop("c", "C", at(5.0), appointmentMin = null, stayMin = 15)

    @Test
    fun `일찍 도착하면 약속 시각까지 기다렸다가 시작한다`() {
        val plan = planner.evaluate(start, depart, listOf(a))
        val arrival = plan.arrivals.single()
        assertEquals(hm(9, 10), arrival.arriveMin)
        assertEquals(hm(12, 0), arrival.startMin)
        assertEquals(hm(12, 30), arrival.departMin)
        assertEquals(170, arrival.slackMin)
        assertEquals(ArrivalStatus.OK, arrival.status)
    }

    @Test
    fun `약속이 없는 방문은 항상 여유 있음이고 바로 시작한다`() {
        val arrival = planner.evaluate(start, depart, listOf(c)).arrivals.single()
        assertNull(arrival.slackMin)
        assertEquals(ArrivalStatus.OK, arrival.status)
        assertEquals(arrival.arriveMin, arrival.startMin)
    }

    @Test
    fun `앞 방문이 길어져 다음 약속에 늦으면 지각으로 계산한다`() {
        val plan = planner.evaluate(start, depart, listOf(a, b))
        val second = plan.arrivals[1]
        assertEquals(hm(12, 40), second.arriveMin)
        assertEquals(-160, second.slackMin)
        assertEquals(ArrivalStatus.LATE, second.status)
        assertEquals(1, plan.lateCount)
        assertEquals(160, plan.totalLateMin)
    }

    @Test
    fun `여유가 10분 미만이면 빠듯함, 음수면 지각`() {
        // 10km 떨어진 곳은 9시 10분 도착
        fun statusFor(appointment: Int) = planner.evaluate(
            start, depart, listOf(Stop("x", "X", at(10.0), appointmentMin = appointment)),
        ).arrivals.single().status

        assertEquals(ArrivalStatus.OK, statusFor(hm(9, 25)))    // 여유 15분
        assertEquals(ArrivalStatus.TIGHT, statusFor(hm(9, 15))) // 여유 5분
        assertEquals(ArrivalStatus.LATE, statusFor(hm(9, 5)))   // 5분 지각
    }

    @Test
    fun `모든 순서를 비교해 지각이 없는 순서를 고른다`() {
        val plan = planner.optimize(start, depart, listOf(a, b))
        assertEquals(listOf("b", "a"), plan.order.map { it.id })
        assertEquals(0, plan.lateCount)
    }

    @Test
    fun `순서가 같게 좋으면 입력 순서를 유지한다`() {
        val x = Stop("x", "X", at(3.0))
        val y = Stop("y", "Y", at(3.0))
        val plan = planner.optimize(start, depart, listOf(x, y))
        assertEquals(listOf("x", "y"), plan.order.map { it.id })
    }

    @Test
    fun `방문지가 3곳을 넘으면 거부한다`() {
        val four = (1..4).map { Stop("s$it", "S$it", at(it.toDouble())) }
        assertThrows(IllegalArgumentException::class.java) { planner.evaluate(start, depart, four) }
        assertThrows(IllegalArgumentException::class.java) { planner.optimize(start, depart, four) }
    }

    @Test
    fun `지연으로 지각하게 되면 순서 변경을 제안한다`() {
        val replan = planner.suggestReplan(start, depart, listOf(a, b))
        assertNotNull(replan)
        assertEquals(listOf("b", "a"), replan!!.suggested.order.map { it.id })
        assertEquals(1, replan.savedLateCount)
        assertEquals(160, replan.savedLateMin)
    }

    @Test
    fun `이미 가장 좋은 순서면 제안하지 않는다`() {
        assertNull(planner.suggestReplan(start, depart, listOf(b, a)))
    }

    @Test
    fun `남은 방문지가 하나면 제안하지 않는다`() {
        assertNull(planner.suggestReplan(start, depart, listOf(a)))
    }

    @Test
    fun `교통 지연 배율은 시간만 늘리고 거리는 그대로 둔다`() {
        val base = LineEstimator()
        val slow = TrafficAdjustedEstimator(base, durationMultiplier = 2.0)
        val leg = slow.estimate(at(0.0), at(10.0))
        assertEquals(10_000, leg.distanceM)
        assertEquals(1200, leg.durationSec)
    }

    @Test
    fun `내비 실측값이 있으면 추정값 대신 쓴다`() {
        val real = Leg(distanceM = 13_000, durationSec = 2_880)
        val cached = CachedEstimator(
            LineEstimator(),
            mapOf(CachedEstimator.LegKey(at(0.0), at(10.0)) to real),
        )
        assertEquals(real, cached.estimate(at(0.0), at(10.0)))
        assertEquals(Leg(5_000, 300), cached.estimate(at(0.0), at(5.0)))
    }

    @Test
    fun `직선거리 추정이 수도권 방문지 사이에서 그럴듯한 값을 낸다`() {
        val gangnam = Point(37.4979, 127.0276)
        val pangyo = Point(37.4021, 127.1086)
        val leg = HaversineEstimator().estimate(gangnam, pangyo)
        assertTrue("거리 ${leg.distanceM}m", leg.distanceM in 12_000..25_000)
        assertTrue("시간 ${leg.durationSec}초", leg.durationSec in 1_200..3_000)
    }

    @Test
    fun `시각 표시`() {
        assertEquals("오후 2시", formatTime(hm(14, 0)))
        assertEquals("오후 2시 30분", formatTime(hm(14, 30)))
        assertEquals("오전 9시 5분", formatTime(hm(9, 5)))
        assertEquals("오전 12시", formatTime(0))
        assertEquals("오후 12시", formatTime(hm(12, 0)))
    }
}
