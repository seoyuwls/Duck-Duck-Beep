package com.example.chargesuggest.duckduck.domain

enum class TicketStatus { AVAILABLE, SENT, EXPIRED }

enum class SendResult { OK, UNKNOWN_TICKET, EXPIRED, ALREADY_SENT, DAILY_LIMIT, RECIPIENT_LIMIT }

/** 만남 한 번에 생기는 티켓. carId는 그 만남에서만 쓰는 익명 값이다 */
data class Ticket(val id: Int, val carId: String, val createdAtMs: Long, val sentAtMs: Long? = null)

data class TicketView(val ticket: Ticket, val status: TicketStatus, val remainingMs: Long)

/**
 * 만남과 티켓 (명세 4장).
 *  - 같은 차를 처음 감지하면 티켓이 생기고 15분 동안 유효하다 (만남 시점부터 계산, 연장 없음)
 *  - 티켓 한 장으로 한 번만 보낼 수 있다
 *  - 하루 보내기 제한, 같은 상대에게 하루 보내기 제한 [명세 밖 제안]
 *  - 여러 장이면 **가장 최근에 만난 한 장만** 제안한다
 * 한 번에 쓰는 스레드는 하나라고 가정한다.
 */
class TicketBook(
    private val validMs: Long = DuckConfig.TICKET_VALID_MS,
    private val dailyLimit: Int = DuckConfig.DAILY_SEND_LIMIT,
    private val perRecipientPerDay: Int = DuckConfig.SEND_PER_RECIPIENT_PER_DAY,
    private val dayOf: (Long) -> Long = DuckConfig::kstDay,
) {
    private val tickets = mutableListOf<Ticket>()
    private var nextId = 1

    /**
     * 같은 차를 감지했을 때. 새 만남이면 티켓을 만들어 돌려주고, 같은 만남(15분 안에 또 감지)이면 null.
     * 돌려받은 티켓이 있을 때만 "발견했어요" 알림을 낸다.
     */
    fun onEncounter(carId: String, nowMs: Long): Ticket? {
        val last = tickets.lastOrNull { it.carId == carId }
        if (last != null && nowMs - last.createdAtMs < validMs) return null
        return Ticket(nextId++, carId, nowMs).also { tickets += it }
    }

    /**
     * 지금 근처에 있는 차(carId)에게 보낼 수 있는 티켓. 없으면 null.
     * 그 차를 만난 지 15분이 지났어도 지금 근처에 있다면 새 만남으로 보고 새 티켓을 만든다. 이미 보냈거나 15분이 안 지난 보낸 티켓이면 null.
     */
    fun availableFor(carId: String, nowMs: Long): Ticket? =
        tickets.lastOrNull { it.carId == carId && statusOf(it, nowMs) == TicketStatus.AVAILABLE } ?: onEncounter(carId, nowMs)

    private fun statusOf(t: Ticket, nowMs: Long) = when {
        t.sentAtMs != null -> TicketStatus.SENT
        nowMs - t.createdAtMs >= validMs -> TicketStatus.EXPIRED
        else -> TicketStatus.AVAILABLE
    }

    private fun remainingOf(t: Ticket, nowMs: Long) = (validMs - (nowMs - t.createdAtMs)).coerceAtLeast(0)

    /** 지금 보낼 수 있는 티켓 중 가장 최근에 만난 한 장. 없으면 null */
    fun suggested(nowMs: Long): Ticket? =
        tickets.filter { statusOf(it, nowMs) == TicketStatus.AVAILABLE }.maxByOrNull { it.createdAtMs }

    /** 오늘의 만남 목록(최근 순). 메인 화면의 "오늘 만난 ○○ N대" */
    fun today(nowMs: Long): List<TicketView> {
        val day = dayOf(nowMs)
        return tickets.filter { dayOf(it.createdAtMs) == day }
            .sortedByDescending { it.createdAtMs }
            .map { TicketView(it, statusOf(it, nowMs), remainingOf(it, nowMs)) }
    }

    /** 오늘 만난 차 대수. 같은 차를 두 번 만나도 한 대로 센다 */
    fun metCarsToday(nowMs: Long): Int = today(nowMs).map { it.ticket.carId }.distinct().size

    fun sentToday(nowMs: Long): Int {
        val day = dayOf(nowMs)
        return tickets.count { it.sentAtMs != null && dayOf(it.sentAtMs) == day }
    }

    fun ticketOf(ticketId: Int): Ticket? = tickets.firstOrNull { it.id == ticketId }

    /** 서버가 전달하지 못했을 때(상대가 꺼짐 등) 보낸 것으로 센 것을 되돌린다. 티켓은 남은 시간만큼 다시 쓸 수 있다 */
    fun revert(ticketId: Int) {
        val idx = tickets.indexOfFirst { it.id == ticketId }
        if (idx >= 0 && tickets[idx].sentAtMs != null) tickets[idx] = tickets[idx].copy(sentAtMs = null)
    }

    /** 보낼 수 있는지만 확인한다(보내지는 않는다). send와 같은 규칙 */
    fun check(ticketId: Int, nowMs: Long): SendResult {
        val t = tickets.firstOrNull { it.id == ticketId } ?: return SendResult.UNKNOWN_TICKET
        if (t.sentAtMs != null) return SendResult.ALREADY_SENT
        if (nowMs - t.createdAtMs >= validMs) return SendResult.EXPIRED
        val day = dayOf(nowMs)
        val sent = tickets.filter { it.sentAtMs != null && dayOf(it.sentAtMs) == day }
        if (sent.size >= dailyLimit) return SendResult.DAILY_LIMIT
        if (sent.count { it.carId == t.carId } >= perRecipientPerDay) return SendResult.RECIPIENT_LIMIT
        return SendResult.OK
    }

    fun send(ticketId: Int, nowMs: Long): SendResult {
        val result = check(ticketId, nowMs)
        if (result != SendResult.OK) return result
        val idx = tickets.indexOfFirst { it.id == ticketId }
        tickets[idx] = tickets[idx].copy(sentAtMs = nowMs)
        return SendResult.OK
    }
}
