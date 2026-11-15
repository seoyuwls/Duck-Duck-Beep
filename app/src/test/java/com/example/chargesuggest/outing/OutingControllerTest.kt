package com.example.chargesuggest.outing

import com.example.chargesuggest.outing.domain.Point
import com.example.chargesuggest.outing.domain.Stop
import com.example.chargesuggest.outing.domain.hm
import com.example.chargesuggest.outing.ports.CumulativeEta
import com.example.chargesuggest.outing.ports.OdometerSource
import com.example.chargesuggest.outing.ports.RouteEvents
import com.example.chargesuggest.outing.ports.RouteGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway : RouteGateway {
    var current: RouteEvents? = null
    val started = ArrayList<List<Stop>>()
    override fun connect(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) = onConnected()
    override fun setEvents(events: RouteEvents?) { current = events }
    override fun startRoute(stops: List<Stop>) { started.add(stops) }
    override fun refreshEtas() {}
    override fun cancelRoute() {}
    override fun release() {}
}

private class FakeOdometer : OdometerSource {
    var push: (Float) -> Unit = {}
    override fun start(onValue: (Float) -> Unit, onError: (String) -> Unit) { push = onValue }
    override fun stop() {}
}

class OutingControllerTest {

    private val start = Point(37.5000, 127.0000)
    private val a = Stop("a", "A 고객사", Point(37.5100, 127.0100), appointmentMin = hm(10, 0))
    private val b = Stop("b", "B 고객사", Point(37.5300, 127.0300), appointmentMin = hm(12, 0))
    private val c = Stop("c", "C 고객사", Point(37.5500, 127.0500), appointmentMin = null)

    private var now = hm(9, 0)
    private val gateway = FakeGateway()
    private val odometer = FakeOdometer()
    private val controller = OutingController(gateway, odometer, clockMin = { now }).also { it.attach() }

    @Test
    fun `계획을 만들면 출발 전 타임라인이 채워진다`() {
        controller.plan(start, hm(9, 0), listOf(a, b, c), optimize = false)
        val s = controller.state.value
        assertEquals(Phase.PLANNED, s.phase)
        assertEquals(listOf("a", "b", "c"), s.plan!!.order.map { it.id })
        assertNull(s.remainingPlan)
    }

    @Test
    fun `드래그로 순서를 바꾸면 즉시 다시 계산한다`() {
        controller.plan(start, hm(9, 0), listOf(a, b, c), optimize = false)
        controller.reorder(listOf(c, b, a))
        assertEquals(listOf("c", "b", "a"), controller.state.value.plan!!.order.map { it.id })
    }

    @Test
    fun `교통 배율을 올리면 이동 시간이 늘어난다`() {
        controller.plan(start, hm(9, 0), listOf(a, b), optimize = false)
        val before = controller.state.value.plan!!.totalTravelSec
        controller.setTrafficMultiplier(2.0)
        assertTrue(controller.state.value.plan!!.totalTravelSec > before)
    }

    @Test
    fun `경로 시작하면 내비에 순서대로 넘기고 통행료를 기억한다`() {
        controller.plan(start, hm(9, 0), listOf(a, b, c), optimize = false)
        controller.startNavigation()
        assertEquals(listOf("a", "b", "c"), gateway.started.single().map { it.id })
        assertEquals(Phase.NAVIGATING, controller.state.value.phase)

        gateway.current!!.onRouteStarted(13_000, 2_880, 1_400)
        assertEquals(1_400, controller.state.value.tollWon)
    }

    @Test
    fun `내비가 구간 값을 주면 추정을 실제 값으로 바꾼다`() {
        controller.plan(start, hm(9, 0), listOf(a, b), optimize = false)
        controller.startNavigation()
        // 누적: A까지 20분, B까지 50분 -> 구간은 20분, 30분
        gateway.current!!.onEtas(
            listOf(CumulativeEta("A", 10_000, 1_200), CumulativeEta("B", 25_000, 3_000)),
        )
        val s = controller.state.value
        assertTrue(s.navEtaAvailable)
        val legs = s.plan!!.arrivals.map { it.leg }
        assertEquals(1_200, legs[0].durationSec)
        assertEquals(1_800, legs[1].durationSec)
        assertEquals(15_000, legs[1].distanceM)
    }

    @Test
    fun `도착 이벤트마다 계기판 차이로 방문 기록을 남긴다`() {
        odometer.push(100f)
        controller.plan(start, hm(9, 0), listOf(a, b), optimize = false)
        controller.startNavigation()

        now = hm(9, 30); odometer.push(112f)
        gateway.current!!.onWaypointArrived("A 고객사", 11_000, 1_800)
        now = hm(10, 40); odometer.push(130f)
        gateway.current!!.onDestinationArrived("B 고객사", 17_000, 2_400)

        val s = controller.state.value
        assertEquals(Phase.DONE, s.phase)
        assertEquals(2, s.visits.size)
        assertEquals(30.0, controller.summary().totalKm, 1e-6)
    }

    @Test
    fun `도착 후 방문 목적을 한 번 탭으로 남긴다`() {
        controller.plan(start, hm(9, 0), listOf(a), optimize = false)
        controller.startNavigation()
        gateway.current!!.onDestinationArrived("A 고객사", 5_000, 600)
        controller.setPurpose(0, "고객 미팅")
        assertEquals("고객 미팅", controller.state.value.visits.single().purpose)
    }

    @Test
    fun `이동이 늦어지면 순서 변경을 제안하고 적용하면 남은 방문지만 다시 넘긴다`() {
        // A는 10시 약속, 멀리 있는 B는 느슨 -> 지연 시 B를 먼저 가는 게 아니라 A가 늦지 않게 유지
        // 반대로 A가 느슨하고 B가 빡빡한 상황을 만든다
        val loose = Stop("loose", "느슨한 곳", Point(37.5100, 127.0100), appointmentMin = hm(15, 0))
        val tight = Stop("tight", "빡빡한 곳", Point(37.5110, 127.0110), appointmentMin = hm(9, 40))
        controller.plan(start, hm(9, 0), listOf(loose, tight), optimize = false)
        controller.startNavigation()
        now = hm(9, 20)
        controller.setTrafficMultiplier(1.0)
        val replan = controller.state.value.replan
        assertNotNull(replan)
        assertEquals("tight", replan!!.suggested.order.first().id)

        controller.applyReplan()
        assertEquals("tight", gateway.started.last().first().id)
        assertNull(controller.state.value.replan)
    }

    @Test
    fun `방문지가 3곳을 넘으면 계획을 거부한다`() {
        val extra = Stop("d", "D", Point(37.56, 127.06), appointmentMin = null)
        val result = runCatching { controller.plan(start, hm(9, 0), listOf(a, b, c, extra)) }
        assertTrue(result.isFailure)
    }
}
