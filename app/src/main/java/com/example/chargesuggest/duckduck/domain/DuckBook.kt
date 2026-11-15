package com.example.chargesuggest.duckduck.domain

/** 받은 오리 한 마리. opened=false면 아직 확인하지 않은 오리 (주행 중에는 종류를 보여 주지 않는다) */
data class BookEntry(
    val id: Int,
    val type: DuckType,
    val fromNickname: String,
    val receivedAtMs: Long,
    val opened: Boolean = false,
)

/** 오리를 하나 확인한 결과. isNewType이면 "새 오리 발견 · 도감에 추가"를 보여 준다 */
data class Opened(val book: DuckBook, val entry: BookEntry, val isNewType: Boolean)

/**
 * 도감 (명세 6.3 R1~R6).
 *  - 오리는 **받는 순간 저장**된다. 확인하지 않고 출발해도 사라지지 않는다.
 *  - 확인(open)은 가장 먼저 온 것부터 한 마리씩 한다.
 *  - 도감에 "모은 오리"로 세는 것은 확인한 것뿐이다. (주행 중에 종류가 새어 나가지 않게)
 */
data class DuckBook(val entries: List<BookEntry> = emptyList(), val nextId: Int = 1) {

    fun receive(type: DuckType, fromNickname: String, nowMs: Long): DuckBook =
        DuckBook(entries + BookEntry(nextId, type, fromNickname, nowMs), nextId + 1)

    /** "새로 온 오리 N" */
    val unreadCount: Int get() = entries.count { !it.opened }

    /** 다음에 확인할 오리 (가장 먼저 온 것). 없으면 null */
    fun nextUnread(): BookEntry? = entries.filter { !it.opened }.minByOrNull { it.id }

    fun openNext(): Opened? = nextUnread()?.let { open(it.id) }

    /**
     * 특정 오리를 확인한다. 이미 확인했거나 없으면 null.
     * 도감에 아직 없는 종류면 "새 오리 발견"(isNewType)이다.
     */
    fun open(entryId: Int): Opened? {
        val e = entries.firstOrNull { it.id == entryId && !it.opened } ?: return null
        val isNew = e.type !in collectedTypes()
        val updated = entries.map { if (it.id == e.id) it.copy(opened = true) else it }
        return Opened(copy(entries = updated), e.copy(opened = true), isNew)
    }

    /** 확인해서 도감에 모은 오리 종류 */
    fun collectedTypes(): Set<DuckType> = entries.filter { it.opened }.map { it.type }.toSet()

    /** "모은 오리 n / 7" */
    fun collectedCount(): Int = collectedTypes().size

    val totalTypes: Int get() = DuckType.values().size

    fun countOf(type: DuckType): Int = entries.count { it.opened && it.type == type }

    /** 이 오리를 보낸 차 목록(닉네임·받은 시각), 최근 순 */
    fun sendersOf(type: DuckType): List<BookEntry> =
        entries.filter { it.opened && it.type == type }.sortedByDescending { it.receivedAtMs }
}
