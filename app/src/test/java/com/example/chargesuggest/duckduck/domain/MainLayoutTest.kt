package com.example.chargesuggest.duckduck.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainLayoutTest {
    // 와이어프레임(1682x1210): 카드 영역 880, 통계 184, 간격 24 -> 두 목록 카드가 나눠 쓰는 높이 648
    private val avail = 648f

    private fun view(id: Int, status: TicketStatus, at: Long = id.toLong()) =
        TicketView(Ticket(id, "c$id", at), status, 0)

    private fun entry(id: Int, opened: Boolean, at: Long = id.toLong()) =
        BookEntry(id, DuckType.BASIC, "닉$id", at, opened)

    // ---- 높이 ----

    @Test
    fun `데이터가 없으면 두 카드는 반반이다`() {
        val (h1, h2) = MainLayout.splitHeights(avail, MainLayout.cardNeed(0).toFloat(), MainLayout.cardNeed(0).toFloat())
        assertEquals(324f, h1, 0.01f)
        assertEquals(324f, h2, 0.01f)
    }

    @Test
    fun `두 카드 모두 두 줄이어도 반반이다 (와이어프레임 325와 317은 측정 오차)`() {
        // 두 줄에 필요한 높이(306)가 절반(324)보다 작으므로 서로 내용을 밀어내지 않는다
        assertTrue(MainLayout.cardNeed(MainLayout.MAX_ROWS) <= 324)
        val (h1, h2) = MainLayout.splitHeights(avail, MainLayout.cardNeed(2).toFloat(), MainLayout.cardNeed(2).toFloat())
        assertEquals(324f, h1, 0.01f)
        assertEquals(324f, h2, 0.01f)
    }

    @Test
    fun `한쪽이 내용 때문에 더 필요하면 그쪽이 커지고 다른 쪽이 줄어든다`() {
        val (h1, h2) = MainLayout.splitHeights(avail, 400f, 200f)
        assertEquals(400f, h1, 0.01f); assertEquals(248f, h2, 0.01f)
        val (g1, g2) = MainLayout.splitHeights(avail, 200f, 400f)
        assertEquals(248f, g1, 0.01f); assertEquals(400f, g2, 0.01f)
    }

    @Test
    fun `둘 다 반을 넘기면 합이 넘치지 않게 나눈다`() {
        val (h1, h2) = MainLayout.splitHeights(avail, 500f, 500f)
        assertEquals(avail, h1 + h2, 0.01f)
    }

    // ---- 줄 서기 ----

    @Test
    fun `마주친 만남은 보낼 수 있는 것만 줄 서고 먼저 만난 것이 위다`() {
        val all = listOf(
            view(4, TicketStatus.AVAILABLE, at = 400), view(3, TicketStatus.EXPIRED, at = 300),
            view(2, TicketStatus.SENT, at = 200), view(1, TicketStatus.AVAILABLE, at = 100),
        )
        // 보낸 것(2)과 지나간 것(3)은 사라진다
        assertEquals(listOf(1, 4), MainLayout.pendingEncounters(all).map { it.ticket.id })
    }

    @Test
    fun `받은 오리는 확인하지 않은 것만 줄 서고 먼저 받은 것이 위다`() {
        val all = listOf(entry(3, false, 300), entry(2, true, 200), entry(1, false, 100))
        assertEquals(listOf(1, 3), MainLayout.pendingReceived(all).map { it.id })
    }

    @Test
    fun `한 번에 두 개만 보이고 나머지는 줄을 선다`() {
        assertEquals(listOf(1, 2), MainLayout.visibleRows(listOf(1, 2, 3, 4)))
        assertEquals(listOf(1), MainLayout.visibleRows(listOf(1)))
        assertEquals(emptyList<Int>(), MainLayout.visibleRows(emptyList<Int>()))
    }

    @Test
    fun `처리하면 사라지고 줄 서 있던 새 알림이 아래로 올라온다`() {
        var all = listOf(entry(1, false), entry(2, false), entry(3, false))
        assertEquals(listOf(1, 2), MainLayout.visibleRows(MainLayout.pendingReceived(all)).map { it.id })
        // 1번을 확인하면 사라지고, 기다리던 3번이 아래에 붙는다
        all = all.map { if (it.id == 1) it.copy(opened = true) else it }
        assertEquals(listOf(2, 3), MainLayout.visibleRows(MainLayout.pendingReceived(all)).map { it.id })
    }

    @Test
    fun `상대 시간을 한국어로 표시한다`() {
        val now = 100 * 60 * 60_000L
        assertEquals("방금 전", MainLayout.relativeTime(now - 20_000, now))
        assertEquals("2분 전", MainLayout.relativeTime(now - 2 * 60_000L, now))
        assertEquals("17분 전", MainLayout.relativeTime(now - 17 * 60_000L, now))
        assertEquals("3시간 전", MainLayout.relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("2일 전", MainLayout.relativeTime(now - 48 * 3_600_000L, now))
        assertEquals("방금 전", MainLayout.relativeTime(now + 5_000, now)) // 시계가 어긋나도 음수가 되지 않는다
    }
}
