package com.example.chargesuggest.duck.nearby

import com.example.chargesuggest.duck.DuckController
import com.example.chargesuggest.duck.DuckReactor
import com.example.chargesuggest.duck.NearbyListener
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.GiveCheck
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.ReactionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceReporterTest {
    private val sent = mutableListOf<Presence>()
    private val r = PresenceReporter("tok") { sent += it }.also {
        it.enabled = true; it.modelKey = "m1"; it.onLocation(37.5665, 126.9780)
    }

    @Test
    fun `처음에는 바로 보내고 격자 번호만 담는다`() {
        r.tick(0)
        assertEquals(1, sent.size)
        assertEquals("tok", sent[0].token)
        assertEquals("m1", sent[0].modelKey)
        assertEquals(Geohash.encode(37.5665, 126.9780), sent[0].cell)
        assertEquals(Geohash.DEFAULT_PRECISION, sent[0].cell.length)   // 정확한 좌표가 아니라 격자 번호
    }

    @Test
    fun `주행 상태에 따라 보내는 간격이 다르다`() {
        for ((mode, interval) in listOf(DriveMode.DRIVING to 5_000L, DriveMode.STOPPED to 10_000L, DriveMode.PARKED to 20_000L)) {
            sent.clear()
            val rep = PresenceReporter("t") { sent += it }.also { it.enabled = true; it.modelKey = "m"; it.driveMode = mode; it.onLocation(37.0, 127.0) }
            rep.tick(0); rep.tick(interval - 1); assertEquals("$mode 직전", 1, sent.size)
            rep.tick(interval); assertEquals("$mode 시점", 2, sent.size)
        }
    }

    @Test
    fun `다른 격자로 옮겨 가면 간격을 기다리지 않고 곧 보낸다`() {
        r.driveMode = DriveMode.PARKED
        r.tick(0)
        r.onLocation(37.5665, 126.9780 + 0.01)       // 한참 옆 격자
        r.tick(1_000); assertEquals(1, sent.size)    // 최소 간격(2초) 전
        r.tick(2_000); assertEquals(2, sent.size)
    }

    @Test
    fun `동의하지 않았거나 차종이나 위치를 모르면 보내지 않는다`() {
        r.enabled = false; r.tick(0); assertTrue(sent.isEmpty())
        r.enabled = true; r.modelKey = null; r.tick(0); assertTrue(sent.isEmpty())
        val noLoc = PresenceReporter("t") { sent += it }.also { it.enabled = true; it.modelKey = "m" }
        noLoc.tick(0); assertTrue(sent.isEmpty())
    }

    @Test
    fun `껐다가 다시 켜면 바로 다시 보낸다`() {
        r.tick(0); r.enabled = false; r.tick(1_000); r.enabled = true; r.tick(1_500)
        assertEquals(2, sent.size)
    }
}

private class NoopReactor : DuckReactor { override fun react(plan: ReactionPlan) {} }

class NearbySimulationTest {
    private var now = 0L
    private val net = SimulatedNearbyNetwork(clockMs = { now }, myToken = "me")
    private val reporter = PresenceReporter("me") { net.report(it) }
    private val foundIds = mutableListOf<String>()
    private val lostIds = mutableListOf<String>()
    private var firstFoundAt: Long? = null
    private var firstLostAt: Long? = null

    init {
        net.setNearbyListener(object : NearbyListener {
            override fun onFound(car: NearbyCar) { foundIds += car.id; if (firstFoundAt == null) firstFoundAt = now }
            override fun onLost(carId: String) { lostIds += carId; if (firstLostAt == null) firstLostAt = now }
        })
        reporter.enabled = true; reporter.modelKey = "m1"; reporter.driveMode = DriveMode.DRIVING
        reporter.onLocation(net.userLat, net.userLon)
    }

    private fun run(seconds: Int) = repeat(seconds) { now += 1_000; reporter.tick(now); net.tick(now) }

    @Test
    fun `같은 차종 봇이 다가오면 발견하고 지나가면 멀어졌다고 알린다`() {
        net.start("m1")
        run(60)
        assertEquals(listOf("bot-same-1"), foundIds)                    // 다른 차종 봇은 한 번도 알리지 않는다
        assertEquals(listOf("bot-same-1"), lostIds)
        assertTrue("발견 시각 ${firstFoundAt}", firstFoundAt!! in 8_000..25_000)
        assertTrue("멀어짐 시각 ${firstLostAt}", firstLostAt!! in 28_000..50_000)
    }

    @Test
    fun `내 차종이 봇과 다르면 아무것도 발견하지 않는다`() {
        reporter.modelKey = "m2"
        net.start("m1")                                                    // 같은 차종 봇은 m1
        run(60)
        assertTrue(foundIds.isEmpty())
    }

    @Test
    fun `동의를 끄면 서버가 나를 몰라서 발견하지 않는다`() {
        reporter.enabled = false
        net.start("m1")
        run(60)
        assertTrue(foundIds.isEmpty())
    }

    @Test
    fun `발견된 동안에는 인형을 보낼 수 있고 한 번 보내면 같은 상대에게 다시 못 보낸다`() {
        net.start("m1")
        run(20)                                                            // 발견 직후
        assertEquals(1, foundIds.size)
        val kind = DuckCatalog.all.first()
        assertEquals(SendResult.OK, net.trySend(foundIds[0], "히힝이", kind))
        assertEquals(SendResult.TARGET_COOLDOWN, net.trySend(foundIds[0], "히힝이", kind))
        assertEquals(1, net.server.receivedGifts[foundIds[0]]?.size)
    }

    @Test
    fun `멀어진 지 한참 지나면 보낼 수 없다`() {
        net.start("m1")
        run(90)                                                            // 지나가고 한참 뒤
        assertEquals(SendResult.NOT_NEARBY, net.trySend("bot-same-1", "히힝이", DuckCatalog.all.first()))
    }

    @Test
    fun `다른 차종 봇에게는 보낼 수 없다`() {
        net.start("m1"); run(20)
        assertEquals(SendResult.NOT_NEARBY, net.trySend("bot-other-1", "히힝이", DuckCatalog.all.first()))
    }

    @Test
    fun `다시 시작하면 새 봇이 출발해서 다시 발견한다`() {
        net.start("m1"); run(60)
        foundIds.clear(); lostIds.clear()
        net.start("m1"); run(60)
        assertEquals(listOf("bot-same-2"), foundIds)
    }

    @Test
    fun `컨트롤러는 발견하면 전달 대상이 생기고 멀어지면 사라진다`() {
        val controller = DuckController(net, NoopReactor(), clockMs = { now }, rng = kotlin.random.Random(1))
        controller.attach()
        controller.setNickname("히힝이")
        net.start("m1")
        run(20)
        assertNotNull(controller.state.value.nearby)
        assertEquals(GiveCheck.ALLOWED, controller.give())
        run(60)
        assertNull(controller.state.value.nearby)                         // 지나가서 사라짐
    }
}
