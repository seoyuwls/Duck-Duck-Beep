package com.example.chargesuggest.duckduck.domain

/**
 * 메인 화면(와이어프레임, 1682×1210 기준)의 배치와 목록 규칙. 화면 코드와 분리해서 테스트한다.
 * 숫자는 와이어프레임 이미지(1.189배 확대본)를 직접 측정해서 dp로 환산한 값이다.
 *
 * 목록 규칙(와이어프레임 설명):
 *  - "오늘 마주친 ○○"와 "오늘 받은 오리"는 주행 중 생긴 이벤트가 쌓이는 곳이고, **한 번에 최대 2개**만 보인다.
 *  - 처리한 이벤트(보낸 만남, 확인한 오리)는 목록에서 **자동으로 사라진다.**
 *  - 2개를 넘으면 나머지는 줄을 서 있다가, 자리가 나면 **새 알림이 아래로 추가**된다. (먼저 온 것이 위)
 *  - 15분이 지나 보낼 수 없게 된 만남도 사라진다. 남은 시간은 보여 주지 않는다(서두르게 되어 위험).
 */
object MainLayout {
    const val CARD_PADDING = 40
    const val TITLE_LINE = 48

    /** 목록 카드는 부제 없이 제목만 있다 */
    const val HEADER = TITLE_LINE
    const val GAP_TO_ROWS = 28
    const val ROW_H = 63
    const val ROW_GAP = 24
    const val CARD_GAP = 24

    /** 목록 카드에 한 번에 보이는 최대 줄 수 */
    const val MAX_ROWS = 2

    /** 빈 카드의 "아직 ○○가 없어요" 문구 영역 최소 높이 */
    private const val EMPTY_AREA = 96

    /** 줄이 rows개일 때 카드가 내용만으로 필요로 하는 높이 */
    fun cardNeed(rows: Int): Int =
        if (rows <= 0) CARD_PADDING * 2 + HEADER + EMPTY_AREA
        else CARD_PADDING * 2 + HEADER + GAP_TO_ROWS + rows * ROW_H + (rows - 1) * ROW_GAP

    /**
     * 두 카드가 나눠 쓰는 높이(avail, 카드 사이 간격 제외)를 나눈다.
     * 기본은 반반이고, 한쪽이 내용 때문에 더 필요하면 그만큼 커지고 나머지가 작아진다.
     */
    fun splitHeights(avail: Float, need1: Float, need2: Float): Pair<Float, Float> {
        val share = avail / 2f
        return when {
            need1 > share -> { val h1 = need1.coerceAtMost(avail - need2.coerceAtMost(share)); h1 to (avail - h1) }
            need2 > share -> { val h2 = need2.coerceAtMost(avail - need1.coerceAtMost(share)); (avail - h2) to h2 }
            else -> share to (avail - share)
        }
    }

    /** "오늘 마주친 ○○"에 줄 서 있는 만남: 지금 보낼 수 있는 것만, 먼저 만난 순서(위가 오래된 것) */
    fun pendingEncounters(all: List<TicketView>): List<TicketView> =
        all.filter { it.status == TicketStatus.AVAILABLE }.sortedBy { it.ticket.createdAtMs }

    /** "오늘 받은 오리"에 줄 서 있는 오리: 아직 확인하지 않은 것만, 먼저 받은 순서 */
    fun pendingReceived(all: List<BookEntry>): List<BookEntry> =
        all.filter { !it.opened }.sortedBy { it.receivedAtMs }

    /** 화면에 보일 줄: 줄 선 것 중 앞의 [max]개 */
    fun <T> visibleRows(pending: List<T>, max: Int = MAX_ROWS): List<T> = pending.take(max)

    /** "방금 전", "17분 전", "3시간 전", "2일 전" */
    fun relativeTime(thenMs: Long, nowMs: Long): String {
        val min = ((nowMs - thenMs) / 60_000L).coerceAtLeast(0)
        return when {
            min < 1 -> "방금 전"
            min < 60 -> "${min}분 전"
            min < 24 * 60 -> "${min / 60}시간 전"
            else -> "${min / (24 * 60)}일 전"
        }
    }
}
