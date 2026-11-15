package com.example.chargesuggest.outing.domain

import kotlin.math.roundToInt

/** 방문 상대와 안건 정보. 지금은 모의 데이터이고, 실제로는 캘린더·메일 연동으로 채운다 */
data class VisitInfo(
    val counterpart: String,
    /** 중요한 것부터 */
    val agenda: List<String>,
    /** 지난 대화나 메일을 한 줄로 */
    val lastContact: String? = null,
    /** 챙길 것 */
    val prepNote: String? = null,
)

data class Briefing(
    val sentences: List<String>,
    val estimatedSeconds: Int,
) {
    val text: String get() = sentences.joinToString(" ")
}

/**
 * 도착 예정까지 남은 시간에 맞춰 길이를 조절하는 음성 브리핑.
 * 주행 중에는 화면 없이 음성만 나가므로 짧고 중요한 것부터 말한다(가이드: 1~2초에 이해, 문장은 짧게).
 */
class BriefingComposer(
    /** 한국어 음성 안내의 대략적인 속도(글자/초). 실제 TTS로 맞춰 보정할 값 */
    private val charsPerSecond: Double = 5.0,
    private val maxSeconds: Int = 45,
    private val minSeconds: Int = 12,
) {
    /** 남은 시간의 약 1/5을 브리핑에 쓰되 [minSeconds]~[maxSeconds]로 제한한다 */
    fun budgetSeconds(minutesToArrival: Int): Int =
        (minutesToArrival * 60 / 5).coerceIn(minSeconds, maxSeconds)

    fun compose(stop: Stop, info: VisitInfo, minutesToArrival: Int): Briefing {
        val budgetChars = (budgetSeconds(minutesToArrival) * charsPerSecond).roundToInt()

        val head = buildString {
            append(stop.name)
            append(", ")
            append(info.counterpart)
            append("님과 ")
            val appt = stop.appointmentMin
            if (appt != null) append(formatTime(appt)).append(" 약속이에요.") else append("방문이에요.")
        }

        // 필수 문장(머리말) 다음에 중요한 순서로 후보를 쌓고, 글자 수 예산 안에서만 채운다
        val candidates = buildList {
            info.agenda.firstOrNull()?.let { add("오늘 안건은 $it.") }
            info.lastContact?.let { add("지난 연락은 $it.") }
            info.agenda.drop(1).forEach { add("그다음은 $it.") }
            info.prepNote?.let { add("챙길 것은 $it.") }
        }

        val sentences = mutableListOf(head)
        var used = head.length
        for (c in candidates) {
            if (used + c.length > budgetChars) continue
            sentences += c
            used += c.length
        }
        return Briefing(sentences, (used / charsPerSecond).roundToInt().coerceAtLeast(1))
    }
}
