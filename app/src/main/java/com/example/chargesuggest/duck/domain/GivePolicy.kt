package com.example.chargesuggest.duck.domain

enum class GiveCheck { ALLOWED, NO_TARGET, DAILY_LIMIT, TARGET_COOLDOWN }

/**
 * 남용 방지: 하루 전달 횟수와 같은 상대에게 다시 보내기를 제한한다.
 * 한 번에 쓰는 스레드는 하나라고 가정한다(화면 스레드).
 */
class GivePolicy(
    private val dailyLimit: Int = 20,
    private val sameTargetCooldownMs: Long = 24 * 3_600_000L,
    /** 시각 -> 날짜 번호. 기본은 한국 시간 자정 기준 */
    private val dayOf: (Long) -> Long = { (it + 9 * 3_600_000L) / 86_400_000L },
) {
    private var day = Long.MIN_VALUE
    private var countToday = 0
    private val lastToTarget = HashMap<String, Long>()

    fun check(targetId: String, nowMs: Long): GiveCheck {
        val count = if (dayOf(nowMs) == day) countToday else 0
        if (count >= dailyLimit) return GiveCheck.DAILY_LIMIT
        val last = lastToTarget[targetId]
        if (last != null && nowMs - last < sameTargetCooldownMs) return GiveCheck.TARGET_COOLDOWN
        return GiveCheck.ALLOWED
    }

    fun record(targetId: String, nowMs: Long) {
        val d = dayOf(nowMs)
        if (d != day) { day = d; countToday = 0 }
        countToday++
        lastToTarget[targetId] = nowMs
    }
}
