package com.example.chargesuggest.duck.nearby

import com.example.chargesuggest.duck.domain.DuckCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceMatcherTest {
    private val here = Geohash.encode(37.5665, 126.9780)
    private val box = Geohash.decodeBox(here)
    private val oneEast = Geohash.encode(37.5665, 126.9780 + box.width)
    private val twoEast = Geohash.encode(37.5665, 126.9780 + 2 * box.width)

    private fun p(token: String, model: String = "m1", cell: String = here, at: Long = 0) = Presence(token, model, cell, at)

    private fun matcher(pairCooldown: Long = 86_400_000L, clientCooldown: Long = 0L) =
        PresenceMatcher(ttlMs = 30_000, pairCooldownMs = pairCooldown, clientCooldownMs = clientCooldown, pairGraceMs = 30_000)

    @Test
    fun `같은 차종이 같은 격자에 있으면 양쪽에 발견을 알린다`() {
        val m = matcher()
        assertTrue(m.update(p("A")).isEmpty())
        val ev = m.update(p("B", at = 1000))
        assertEquals(setOf<NearbyEvent>(NearbyEvent.Found("A", "B"), NearbyEvent.Found("B", "A")), ev.toSet())
    }

    @Test
    fun `이웃 격자도 근처로 보고 두 칸 떨어지면 아니다`() {
        val near = matcher(); near.update(p("A")); assertEquals(2, near.update(p("B", cell = oneEast, at = 1)).size)
        val far = matcher(); far.update(p("A")); assertTrue(far.update(p("B", cell = twoEast, at = 1)).isEmpty())
    }

    @Test
    fun `차종이 다르면 알리지 않는다`() {
        val m = matcher(); m.update(p("A", model = "m1"))
        assertTrue(m.update(p("B", model = "m2", at = 1)).isEmpty())
    }

    @Test
    fun `이미 알린 짝은 다시 알리지 않는다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1))
        assertTrue(m.update(p("A", at = 2000)).isEmpty())
        assertTrue(m.update(p("B", at = 3000)).isEmpty())
    }

    @Test
    fun `멀어지면 양쪽에 멀어짐을 알린다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1))
        val ev = m.update(p("B", cell = twoEast, at = 5000))
        assertEquals(setOf<NearbyEvent>(NearbyEvent.Lost("A", "B"), NearbyEvent.Lost("B", "A")), ev.toSet())
        assertTrue(m.announcedWith("A").isEmpty())
    }

    @Test
    fun `한쪽 소식이 끊기면 시간이 지난 뒤 멀어짐을 알린다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1000))
        // B는 계속 알리지만 A는 소식이 없다
        val ev = m.update(p("B", at = 40_000))
        assertTrue(ev.contains(NearbyEvent.Lost("B", "A")))
    }

    @Test
    fun `알리지 않은 짝에게는 멀어짐도 알리지 않는다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1000))
        m.update(p("B", cell = twoEast, at = 2000))                  // 멀어져서 Lost, 쿨다운이 24시간이라 다시 가까워져도 안 알림
        assertTrue(m.update(p("B", at = 3000)).isEmpty())
        assertTrue(m.update(p("B", cell = twoEast, at = 4000)).isEmpty())   // 알린 적 없는 짝이므로 멀어짐도 없음
    }

    @Test
    fun `같은 짝은 쿨다운이 지나야 다시 알린다`() {
        val m = matcher(pairCooldown = 20_000)
        m.update(p("A")); m.update(p("B", at = 1000))
        m.update(p("B", cell = twoEast, at = 2000))                  // 멀어짐
        assertTrue(m.update(p("B", at = 5000)).isEmpty())             // 쿨다운 안: 알리지 않음
        m.update(p("B", cell = twoEast, at = 6000))
        m.update(p("A", at = 25_000))
        val again = m.update(p("B", at = 26_000))                    // 쿨다운 지남
        assertTrue(again.any { it is NearbyEvent.Found })
    }

    @Test
    fun `한 대에게는 연달아 알리지 않는다`() {
        val m = matcher(clientCooldown = 60_000)
        m.update(p("A")); m.update(p("B", at = 1000))                 // A-B 알림
        val ev = m.update(p("C", at = 2000))                          // A나 B와 짝이 되려 하지만 쿨다운
        assertTrue(ev.none { it is NearbyEvent.Found })
    }

    @Test
    fun `인형 보내기는 알려 준 짝에게만 허용한다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1000))
        assertTrue(m.isGiftAllowed("A", "B", 2000))
        assertTrue(m.isGiftAllowed("B", "A", 2000))
        assertFalse(m.isGiftAllowed("A", "C", 2000))                  // 모르는 상대
    }

    @Test
    fun `멀어져도 유예 시간 안에는 보낼 수 있고 지나면 못 보낸다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1000))
        m.update(p("B", cell = twoEast, at = 5000))                  // 멀어짐 (마지막 근처 시각 1000)
        assertTrue(m.isGiftAllowed("A", "B", 25_000))
        assertFalse(m.isGiftAllowed("A", "B", 40_000))
    }

    @Test
    fun `서버의 존재 정보는 오래되면 지워진다`() {
        val m = matcher(); m.update(p("A")); m.update(p("B", at = 1000))
        m.expire(100_000)
        assertTrue(m.announcedWith("A").isEmpty())
    }
}

class SimServerTest {
    private val here = Geohash.encode(37.5665, 126.9780)
    private val kind = DuckCatalog.all.first()

    private fun server() = SimServer(PresenceMatcher(clientCooldownMs = 0))

    private fun paired(s: SimServer): SimServer {
        s.update(Presence("A", "m1", here, 0)); s.update(Presence("B", "m1", here, 1000)); return s
    }

    @Test
    fun `근처인 상대에게는 보낼 수 있고 상대가 받은 것으로 기록된다`() {
        val s = paired(server())
        assertEquals(SendResult.OK, s.send("A", "B", "히힝이", kind, 2000))
        assertEquals(listOf(kind), s.receivedGifts["B"])
        assertTrue(s.receivedGifts["A"].isNullOrEmpty())              // 보낸 쪽은 받지 않는다 (일방향)
    }

    @Test
    fun `근처가 아니면 거절한다`() {
        val s = server()
        s.update(Presence("A", "m1", here, 0))
        assertEquals(SendResult.NOT_NEARBY, s.send("A", "Z", "히힝이", kind, 1000))
    }

    @Test
    fun `금칙어 닉네임은 서버에서도 거절한다`() {
        val s = paired(server())
        assertEquals(SendResult.BAD_NICKNAME, s.send("A", "B", "씨발오리", kind, 2000))
        assertTrue(s.receivedGifts["B"].isNullOrEmpty())
    }

    @Test
    fun `같은 상대에게 다시 보내면 거절한다`() {
        val s = paired(server())
        assertEquals(SendResult.OK, s.send("A", "B", "히힝이", kind, 2000))
        assertEquals(SendResult.TARGET_COOLDOWN, s.send("A", "B", "히힝이", kind, 3000))
    }
}
