package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.ReactionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckLinkTest {
    private class Rec : DuckReactor {
        val plans = mutableListOf<ReactionPlan>()
        override fun react(plan: ReactionPlan) { plans += plan }
    }

    private val link = DuckLink { 1_000L }
    private val meReactor = Rec()
    private val otherReactor = Rec()
    private val me = DuckController(link.a, meReactor, clockMs = { 12 * 3_600_000L }).also { it.attach() }
    private val other = DuckController(link.b, otherReactor, clockMs = { 12 * 3_600_000L }).also { it.attach() }

    @Test
    fun `내 차가 보내면 상대 차 도감에 닉네임과 함께 쌓이고 상대 차가 반응한다`() {
        me.setNickname("히힝이"); me.setNearby(NearbyCar("other"))
        assertEquals(com.example.chargesuggest.duck.domain.GiveCheck.ALLOWED, me.give())

        val got = other.state.value
        assertEquals(1, got.collection.items.size)
        assertEquals("히힝이", got.collection.items[0].fromNickname)
        assertNotNull(got.card)
        assertTrue(otherReactor.plans.single().playSound)
        // 보낸 쪽은 받은 것이 없다 (일방향)
        assertEquals(0, me.state.value.collection.items.size)
    }

    @Test
    fun `받은 쪽은 되돌려줄 수 없고 상대가 근처로 잡혀야 보낼 수 있다`() {
        me.setNickname("히힝이"); me.setNearby(NearbyCar("other")); me.give()
        other.setNickname("도로위포니")
        // 상대 차가 내 차를 발견하지 않았으면 보낼 수 없다
        assertEquals(com.example.chargesuggest.duck.domain.GiveCheck.NO_TARGET, other.give())
        assertEquals(0, me.state.value.collection.items.size)
    }

    @Test
    fun `인형마다 받은 기록 번호가 다르다`() {
        me.setNickname("히힝이"); me.setNearby(NearbyCar("o1")); me.give()
        me.setNearby(NearbyCar("o2")); me.give()
        val ids = other.state.value.collection.items.map { it.id }
        assertEquals(2, ids.toSet().size)
    }
}
