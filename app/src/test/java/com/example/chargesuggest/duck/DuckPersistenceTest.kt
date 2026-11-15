package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.ReactionPlan
import com.example.chargesuggest.duck.domain.Rarity
import com.example.chargesuggest.duck.domain.ReceivedDuck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuckPersistenceTest {
    private val basic = DuckCatalog.all.first()
    private fun duck(id: String, from: String = "히힝이", at: Long = 1000) = ReceivedDuck(id, from, basic, at)

    private class FakeReactor : DuckReactor { override fun react(plan: ReactionPlan) {} }

    private fun controller(store: KeyValueStore, prefix: String = "me", network: DuckNetwork = MockDuckNetwork { 0L }) =
        DuckController(
            network, FakeReactor(), clockMs = { 12 * 3_600_000L }, rng = Random(1),
            persistence = DuckPersistence(store, prefix),
        ).also { it.attach() }

    @Test
    fun `저장한 값을 그대로 불러온다`() {
        val store = MemoryStore()
        val p = DuckPersistence(store, "me")
        val c = DuckController(MockDuckNetwork { 0L }, FakeReactor(), persistence = p)
        c.setNickname("꽥꽥이"); c.setNotifyWhileDriving(false); c.onReceived(duck("a")); c.onReceived(duck("b", "새벽포니", 2000))

        val saved = p.load()!!
        assertEquals("꽥꽥이", saved.nickname)
        assertFalse(saved.notifyWhileDriving)
        assertEquals(listOf("b", "a"), saved.items.map { it.id })       // 최신순 그대로
        assertEquals("새벽포니", saved.items.first().fromNickname)
        assertEquals(2000L, saved.items.first().receivedAtMs)
        assertEquals(basic, saved.items.first().kind)
    }

    @Test
    fun `앱을 다시 켜면(새 컨트롤러) 도감과 닉네임과 설정이 그대로다`() {
        val store = MemoryStore()
        controller(store).also { it.setNickname("꽥꽥이"); it.setNotifyWhileDriving(false); it.onReceived(duck("a")) }

        val again = controller(store)
        assertEquals("꽥꽥이", again.state.value.nickname)
        assertFalse(again.state.value.settings.notifyWhileDriving)
        assertEquals(1, again.state.value.collection.items.size)
        assertEquals("히힝이", again.state.value.collection.items[0].fromNickname)
    }

    @Test
    fun `보낸 개수도 유지된다`() {
        val store = MemoryStore()
        val net = MockDuckNetwork { 0L }
        val c = controller(store, network = net)
        c.setNickname("꽥꽥이"); c.setNearby(NearbyCar("x")); c.give()
        assertEquals(1, controller(store).state.value.sentCount)
    }

    @Test
    fun `저장하지 않는 값은 매번 새로 시작한다`() {
        val store = MemoryStore()
        val c = controller(store)
        c.setNearby(NearbyCar("x")); c.onReceived(duck("a"))                 // 근처 상대와 카드는 저장 대상이 아니다
        val again = controller(store).state.value
        assertNull(again.nearby)
        assertNull(again.card)
    }

    @Test
    fun `처음에는 저장된 값이 없어서 빈 상태로 시작한다`() {
        val s = controller(MemoryStore()).state.value
        assertEquals("", s.nickname)
        assertTrue(s.collection.items.isEmpty())
        assertTrue(s.settings.notifyWhileDriving)
    }

    @Test
    fun `보내는 차와 받는 차는 따로 저장된다`() {
        val store = MemoryStore()
        controller(store, "me").also { it.setNickname("보내는이"); it.onReceived(duck("m1")) }
        controller(store, "other").also { it.setNickname("받는이") }
        assertEquals("보내는이", controller(store, "me").state.value.nickname)
        assertEquals(1, controller(store, "me").state.value.collection.items.size)
        assertEquals("받는이", controller(store, "other").state.value.nickname)
        assertTrue(controller(store, "other").state.value.collection.items.isEmpty())
    }

    @Test
    fun `금칙어 닉네임은 저장되지 않는다`() {
        val store = MemoryStore()
        val c = controller(store)
        c.setNickname("꽥꽥이"); c.setNickname("씨발오리")
        assertEquals("꽥꽥이", controller(store).state.value.nickname)
    }

    @Test
    fun `깨진 저장값은 무시하고 빈 상태로 시작한다`() {
        val store = MemoryStore().also { it.put("duck_state_me", "{이건 JSON이 아님") }
        assertNull(DuckPersistence(store, "me").load())
        assertTrue(controller(store).state.value.collection.items.isEmpty())
    }

    @Test
    fun `지금 목록에 없는 인형 종류는 건너뛰고 나머지는 살린다`() {
        val store = MemoryStore()
        val old = DuckKind("pony_removed", "없어진 포니", Rarity.RARE)
        DuckPersistence(store, "me", catalog = DuckCatalog.all + old).save(
            DuckState(collection = com.example.chargesuggest.duck.domain.DuckCollection(
                listOf(ReceivedDuck("1", "a", old, 1), ReceivedDuck("2", "b", basic, 2)),
            )),
        )
        val items = DuckPersistence(store, "me").load()!!.items      // 기본 목록에는 pony_removed가 없다
        assertEquals(listOf("2"), items.map { it.id })
    }

    @Test
    fun `초기화하면 도감과 보낸 개수가 비고 닉네임은 남는다`() {
        val store = MemoryStore()
        val c = controller(store)
        c.setNickname("꽥꽥이"); c.onReceived(duck("a"))
        c.clearCollection()
        val again = controller(store).state.value
        assertEquals("꽥꽥이", again.nickname)
        assertTrue(again.collection.items.isEmpty())
        assertEquals(0, again.sentCount)
    }

    @Test
    fun `저장 형식에 닉네임과 종류 번호 외의 개인 정보는 없다`() {
        val store = MemoryStore()
        controller(store).also { it.setNickname("꽥꽥이"); it.onReceived(duck("a")) }
        val raw = store.get("duck_state_me")!!
        assertNotNull(raw)
        assertFalse("위치나 차대번호 같은 값이 들어가면 안 된다", raw.contains("lat") || raw.contains("vin", ignoreCase = true))
    }
}
