package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.DuckContext
import com.example.chargesuggest.duck.domain.GiveCheck
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.NicknameCheck
import com.example.chargesuggest.duck.domain.ReactionPlan
import com.example.chargesuggest.duck.domain.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class FakeReactor : DuckReactor {
    val plans = mutableListOf<ReactionPlan>()
    override fun react(plan: ReactionPlan) { plans += plan }
}

class DuckControllerTest {
    private val network = MockDuckNetwork { 0L }
    private val reactor = FakeReactor()
    private var now = 12 * 3_600_000L // 낮
    private var contexts = emptySet<DuckContext>()
    private var navi = false
    private var visible = true
    // 컨트롤러를 여러 번 만들어도 같은 시드로 다시 시작하지 않도록 하나를 공유한다
    private val rng = Random(5)

    private fun controller() = DuckController(
        network, reactor,
        vehicleContext = { contexts },
        clockMs = { now },
        rng = rng,
        naviSpeaking = { navi },
        appVisible = { visible },
    ).also { it.attach() }

    @Test
    fun `근처 차가 없으면 보낼 수 없다`() {
        val c = controller(); c.setNickname("포니")
        assertEquals(GiveCheck.NO_TARGET, c.give())
        assertTrue(network.sent.isEmpty())
    }

    @Test
    fun `닉네임이 없으면 보낼 수 없다`() {
        val c = controller(); c.setNearby(NearbyCar("x"))
        assertEquals(GiveCheck.NO_TARGET, c.give())
    }

    @Test
    fun `금칙어 닉네임은 저장되지 않는다`() {
        val c = controller()
        assertEquals(NicknameCheck.BANNED, c.setNickname("씨발포니"))
        assertEquals("", c.state.value.nickname)
        assertEquals(NicknameCheck.OK, c.setNickname("  히힝이 "))
        assertEquals("히힝이", c.state.value.nickname)
    }

    @Test
    fun `전달하면 상대에게 인형이 가고 내 차도 반응한다`() {
        val c = controller(); c.setNickname("히힝이"); c.setNearby(NearbyCar("car1"))
        assertEquals(GiveCheck.ALLOWED, c.give())
        assertEquals(1, network.sent.size)
        assertEquals("car1", network.sent[0].targetId)
        assertEquals("히힝이", network.sent[0].fromNickname)
        assertEquals(1, c.state.value.sentCount)
        assertEquals(1, reactor.plans.size)
    }

    @Test
    fun `같은 상대에게 연속으로 보내면 막는다`() {
        val c = controller(); c.setNickname("히힝이"); c.setNearby(NearbyCar("car1"))
        c.give()
        assertEquals(GiveCheck.TARGET_COOLDOWN, c.give())
        assertEquals(1, network.sent.size)
    }

    @Test
    fun `비 오는 날 준 인형은 우산 포니가 나올 수 있다`() {
        contexts = setOf(DuckContext.RAINY)
        val seen = mutableSetOf<String>()
        repeat(300) { i ->
            val c = controller(); c.setNickname("닉"); c.setNearby(NearbyCar("t$i")); c.give()
        }
        network.sent.forEach { seen += it.kind.id }
        assertTrue("pony_umbrella" in seen)
    }

    @Test
    fun `밤에 보내면 밤 인형이 나올 수 있고 낮에는 나오지 않는다`() {
        now = 14 * 3_600_000L // UTC 14시 = 한국 23시
        repeat(300) { i -> val c = controller(); c.setNickname("닉"); c.setNearby(NearbyCar("n$i")); c.give() }
        assertTrue(network.sent.any { it.kind.id == "pony_stars" })

        network.sent.clear()
        now = 3 * 3_600_000L // 한국 12시
        repeat(300) { i -> val c = controller(); c.setNickname("닉"); c.setNearby(NearbyCar("d$i")); c.give() }
        assertTrue(network.sent.none { it.kind.id == "pony_stars" })
    }

    @Test
    fun `받으면 도감에 쌓이고 카드가 뜨고 차가 반응한다`() {
        val c = controller()
        network.simulateIncoming("지나가는 포니", DuckCatalog.all.last())
        val s = c.state.value
        assertEquals(1, s.collection.items.size)
        assertEquals("지나가는 포니", s.card?.fromNickname)
        assertEquals(Rarity.LEGENDARY.ambientRgb, reactor.plans.single().ambientRgb)
    }

    @Test
    fun `앱이 화면에 없으면 카드는 없지만 도감과 반응은 그대로다`() {
        visible = false
        val c = controller()
        network.simulateIncoming()
        assertNull(c.state.value.card)
        assertEquals(1, c.state.value.collection.items.size)
        assertTrue(reactor.plans.single().playSound)
    }

    @Test
    fun `주행 중 알림을 끄면 조용히 도감에만 쌓인다`() {
        val c = controller()
        c.setDriveMode(DriveMode.DRIVING); c.setNotifyWhileDriving(false)
        network.simulateIncoming()
        assertEquals(1, c.state.value.collection.items.size)
        assertNull(c.state.value.card)
        assertEquals(ReactionPlan.NONE, reactor.plans.single())
    }

    @Test
    fun `내비 안내 중이면 효과음이 작아진다`() {
        navi = true
        controller(); network.simulateIncoming()
        assertEquals(0.3f, reactor.plans.single().soundVolume, 1e-6f)
    }

    @Test
    fun `카드는 지울 수 있다`() {
        val c = controller(); network.simulateIncoming()
        assertNotNull(c.state.value.card)
        c.dismissCard()
        assertNull(c.state.value.card)
    }

    @Test
    fun `도감은 주행 중에는 열 수 없다`() {
        val c = controller()
        c.setDriveMode(DriveMode.DRIVING); assertTrue(!c.canOpenCollection())
        c.setDriveMode(DriveMode.PARKED); assertTrue(c.canOpenCollection())
    }
}
