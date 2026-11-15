package com.example.chargesuggest.duck.nearby

import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.GiveCheck
import com.example.chargesuggest.duck.domain.GivePolicy
import com.example.chargesuggest.duck.domain.NicknameCheck
import com.example.chargesuggest.duck.domain.NicknameFilter

/** 서버가 인형 보내기를 받아 준 결과 */
enum class SendResult { OK, NOT_NEARBY, BAD_NICKNAME, DAILY_LIMIT, TARGET_COOLDOWN }

/**
 * 시뮬레이션용 서버. PresenceMatcher(짝짓기)에 서버가 다시 검사해야 하는 규칙을 더했다.
 * 앱의 검사만 믿지 않고 서버에서도 근처인지, 닉네임이 괜찮은지, 횟수를 넘지 않았는지 확인한다.
 */
class SimServer(
    private val matcher: PresenceMatcher = PresenceMatcher(),
    private val nicknameFilter: NicknameFilter = NicknameFilter(),
    private val policyFactory: () -> GivePolicy = { GivePolicy() },
) {
    private val policies = HashMap<String, GivePolicy>()
    private val _gifts = HashMap<String, MutableList<DuckKind>>()

    /** 인형을 받은 차(토큰)별 받은 인형. 시험과 화면 표시용 */
    val receivedGifts: Map<String, List<DuckKind>> get() = _gifts

    fun update(p: Presence): List<NearbyEvent> = matcher.update(p)
    fun expire(nowMs: Long): List<NearbyEvent> = matcher.expire(nowMs)
    fun announcedWith(token: String): Set<String> = matcher.announcedWith(token)

    fun send(from: String, to: String, nickname: String, kind: DuckKind, nowMs: Long): SendResult {
        if (nicknameFilter.check(nickname) != NicknameCheck.OK) return SendResult.BAD_NICKNAME
        if (!matcher.isGiftAllowed(from, to, nowMs)) return SendResult.NOT_NEARBY
        val policy = policies.getOrPut(from) { policyFactory() }
        when (policy.check(to, nowMs)) {
            GiveCheck.DAILY_LIMIT -> return SendResult.DAILY_LIMIT
            GiveCheck.TARGET_COOLDOWN -> return SendResult.TARGET_COOLDOWN
            else -> Unit
        }
        policy.record(to, nowMs)
        _gifts.getOrPut(to) { mutableListOf() }.add(kind)
        return SendResult.OK
    }
}
