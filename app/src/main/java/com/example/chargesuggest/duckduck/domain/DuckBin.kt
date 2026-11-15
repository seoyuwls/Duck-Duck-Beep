package com.example.chargesuggest.duckduck.domain

/** 통 안의 오리 한 마리. id는 화면이 "같은 오리"를 알아보는 번호다 */
/**
 * 통 안의 오리 한 마리. id는 화면이 "같은 오리"를 알아보는 번호다.
 * fresh는 방금 받아서 아직 확인하지 않은 오리(화면에서 받은 오리로 표시한다), entryId는 도감 기록(BookEntry)의 번호다.
 * gift는 다른 사람이 보낸 것이 아니라 그날 날씨가 가져다준 오리(위에서 떨어진다)다.
 */
data class BinDuck(val id: Int, val type: DuckType, val fresh: Boolean = false, val entryId: Int = 0, val gift: Boolean = false)

/**
 * 오리 통(메인 화면의 오리 더미). SDK에 의존하지 않는 순수 규칙이다.
 *  - 처음에는 기본 오리로 가득 채워서 시작한다.
 *  - 오리를 받으면 통에 들어온다. 꽉 차 있으면 가장 오래된 오리가 나간다(도감 기록은 따로 남아 있다).
 *  - 오리를 보내면 통에서 한 마리가 빠진다. 같은 종류가 있으면 그중 가장 오래된 것, 없으면 가장 오래된 것.
 * ducks는 오래된 순서(앞이 가장 오래됨)다.
 */
data class DuckBin(
    val ducks: List<BinDuck> = emptyList(),
    val nextId: Int = 1,
    val capacity: Int = DuckConfig.BIN_CAPACITY,
) {
    val size: Int get() = ducks.size

    /** 받은 오리를 넣는다. 반환: 새 통과, 꽉 차서 밀려난 오리(없으면 null) */
    fun add(type: DuckType, fresh: Boolean = false, entryId: Int = 0, gift: Boolean = false): Pair<DuckBin, BinDuck?> {
        val evicted = if (ducks.size >= capacity) ducks.first() else null
        val kept = if (evicted != null) ducks.drop(1) else ducks
        return copy(ducks = kept + BinDuck(nextId, type, fresh, entryId, gift), nextId = nextId + 1) to evicted
    }

    /** 그 도감 기록의 오리를 확인했다: 받은 오리 표시를 지운다 */
    fun markOpened(entryId: Int): DuckBin =
        if (ducks.none { it.fresh && it.entryId == entryId }) this
        else copy(ducks = ducks.map { if (it.entryId == entryId) it.copy(fresh = false) else it })

    /** 보낸 오리를 뺀다. 비어 있으면 그대로. 반환: 새 통과, 빠진 오리 */
    fun sendOne(type: DuckType): Pair<DuckBin, BinDuck?> {
        val out = ducks.firstOrNull { it.type == type } ?: ducks.firstOrNull() ?: return this to null
        return copy(ducks = ducks.filter { it.id != out.id }) to out
    }

    /** 오리를 직접 골라서 보낼 때(이후 단계) */
    fun remove(id: Int): DuckBin = copy(ducks = ducks.filter { it.id != id })

    companion object {
        /**
         * 처음 쓰는 사람의 통: 기본 오리로 가득 채우고, 눈사람 오리와 우산 오리를 한 마리씩 섞는다.
         * 섞는 자리(0부터 센 순서)는 통이 가득 찼을 때 오래된 순으로 나가는 것을 고려해 가운데쯤으로 둔다.
         */
        fun starter(capacity: Int = DuckConfig.BIN_CAPACITY): DuckBin {
            // 날씨 오리는 그날 날씨가 가져다주므로(희귀) 기본은 기본 오리로만 시작한다. STARTER_SPECIAL_DUCKS로 시연용 두 마리를 섞을 수 있다
            val special = if (DuckConfig.STARTER_SPECIAL_DUCKS) mapOf(3 to DuckType.SNOWMAN, 6 to DuckType.UMBRELLA) else emptyMap()
            return DuckBin(List(capacity) { BinDuck(it + 1, special[it] ?: DuckType.BASIC) }, nextId = capacity + 1, capacity = capacity)
        }
    }
}
