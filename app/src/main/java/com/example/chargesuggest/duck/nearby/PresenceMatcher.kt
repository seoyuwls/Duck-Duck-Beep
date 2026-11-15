package com.example.chargesuggest.duck.nearby

/*
 * 서버가 하는 일을 순수 코드로 만든 것 (docs/nearby-design.md 4.2).
 * 지금은 앱 안의 시뮬레이션이 이 클래스를 쓰고, 실제 서버를 만들면 같은 규칙으로 옮긴다.
 * 위치는 격자 번호로만 다루고, 아무것도 디스크에 저장하지 않는다.
 */

/** 차가 서버에 알리는 "나 여기 있어요". token은 앱을 켤 때마다 새로 만드는 익명 번호 */
data class Presence(val token: String, val modelKey: String, val cell: String, val atMs: Long)

sealed interface NearbyEvent {
    /** 이 알림을 받을 차 */
    val forToken: String
    /** 근처에 있는(있던) 상대 */
    val otherToken: String

    /** 같은 차종이 근처에 나타났다 */
    data class Found(override val forToken: String, override val otherToken: String) : NearbyEvent

    /** 알려 준 상대가 멀어지거나 사라졌다 */
    data class Lost(override val forToken: String, override val otherToken: String) : NearbyEvent
}

class PresenceMatcher(
    /** 마지막 알림 이후 이 시간이 지나면 그 차는 없는 것으로 본다 */
    private val ttlMs: Long = 30_000,
    /** 같은 짝에게 다시 "발견"을 알리기까지의 시간 (실서비스는 24시간) */
    private val pairCooldownMs: Long = 24 * 3_600_000L,
    /** 한 대에게 연달아 "발견"을 알리지 않는 시간 */
    private val clientCooldownMs: Long = 60_000,
    /** 멀어진 뒤에도 이 시간 안에는 인형을 보낼 수 있다 (버튼을 누르는 순간 멀어진 경우를 위해) */
    private val pairGraceMs: Long = 30_000,
    private val cellsAround: (String) -> Set<String> = Geohash::cellAndNeighbors,
) {
    private data class Pair(val a: String, val b: String) {
        operator fun contains(t: String) = t == a || t == b
        fun other(t: String) = if (t == a) b else a
    }

    private fun pairOf(x: String, y: String) = if (x < y) Pair(x, y) else Pair(y, x)

    private val presences = HashMap<String, Presence>()
    /** 지금 알려 준 상태인 짝 */
    private val announced = HashSet<Pair>()
    /** 알려 준 짝이 마지막으로 근처였던 시각 (인형 보내기 검증용) */
    private val lastNearbyAt = HashMap<Pair, Long>()
    private val lastAnnouncedAt = HashMap<Pair, Long>()
    private val lastAnnouncedForClient = HashMap<String, Long>()

    /** 차 한 대의 알림을 받는다. 그 결과로 생기는 발견·멀어짐 알림을 돌려준다 */
    fun update(p: Presence): List<NearbyEvent> {
        val events = ArrayList<NearbyEvent>()
        events += expire(p.atMs)
        presences[p.token] = p

        val cells = cellsAround(p.cell)
        val current = presences.values
            .filter { it.token != p.token && it.modelKey == p.modelKey && it.cell in cells }
            .map { pairOf(p.token, it.token) }
            .toSet()

        // 멀어진 짝
        announced.filter { p.token in it && it !in current }.forEach {
            announced.remove(it)
            events += lost(it)
        }
        // 가까운 짝: 처음이면 알리고, 이미 알렸으면 근처 시각만 갱신한다
        for (pair in current) {
            if (pair in announced) { lastNearbyAt[pair] = p.atMs; continue }
            if (canAnnounce(pair, p.atMs)) {
                announced += pair
                lastNearbyAt[pair] = p.atMs
                lastAnnouncedAt[pair] = p.atMs
                lastAnnouncedForClient[pair.a] = p.atMs
                lastAnnouncedForClient[pair.b] = p.atMs
                events += NearbyEvent.Found(pair.a, pair.b)
                events += NearbyEvent.Found(pair.b, pair.a)
            }
        }
        return events
    }

    /** 오래 소식이 없는 차를 지우고, 그 차와 알려 둔 짝에는 멀어짐을 알린다 */
    fun expire(nowMs: Long): List<NearbyEvent> {
        val events = ArrayList<NearbyEvent>()
        val gone = presences.values.filter { nowMs - it.atMs > ttlMs }.map { it.token }
        for (t in gone) {
            presences.remove(t)
            announced.filter { t in it }.forEach { announced.remove(it); events += lost(it) }
        }
        // 오래된 기록 정리 (메모리가 계속 늘지 않게)
        lastNearbyAt.entries.removeAll { nowMs - it.value > pairGraceMs * 4 }
        lastAnnouncedAt.entries.removeAll { nowMs - it.value > pairCooldownMs }
        lastAnnouncedForClient.entries.removeAll { nowMs - it.value > clientCooldownMs }
        return events
    }

    /** 이 차가 지금 알려 둔(발견 상태인) 상대들 */
    fun announcedWith(token: String): Set<String> = announced.filter { token in it }.map { it.other(token) }.toSet()

    /** 두 차 사이에 인형을 보내도 되는가: 발견을 알려 준 짝이고, 최근에 근처였을 때만 */
    fun isGiftAllowed(from: String, to: String, nowMs: Long): Boolean {
        val pair = pairOf(from, to)
        val seen = lastNearbyAt[pair] ?: return false
        return nowMs - seen <= pairGraceMs
    }

    private fun canAnnounce(pair: Pair, nowMs: Long): Boolean {
        val pairLast = lastAnnouncedAt[pair]
        if (pairLast != null && nowMs - pairLast < pairCooldownMs) return false
        for (t in listOf(pair.a, pair.b)) {
            val last = lastAnnouncedForClient[t]
            if (last != null && nowMs - last < clientCooldownMs) return false
        }
        return true
    }

    private fun lost(pair: Pair): List<NearbyEvent> = listOf(
        NearbyEvent.Lost(pair.a, pair.b),
        NearbyEvent.Lost(pair.b, pair.a),
    )
}
