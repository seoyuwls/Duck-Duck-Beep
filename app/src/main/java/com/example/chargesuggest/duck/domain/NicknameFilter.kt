package com.example.chargesuggest.duck.domain

enum class NicknameCheck { OK, EMPTY, TOO_LONG, BANNED }

/**
 * 닉네임은 자유 입력이고 부적절한 단어만 막는다.
 * 공백·특수문자를 끼워 넣어 우회하는 경우를 줄이려고 글자와 숫자만 남겨서 비교한다.
 * TODO(verify): 아래 금칙어는 임시 목록이다. 제출 전에 실제 금칙어 목록으로 교체해야 한다.
 */
class NicknameFilter(
    private val banned: Set<String> = DEFAULT_BANNED,
    val maxLength: Int = 10,
) {
    private val normalizedBanned = banned.map(::normalize).filter { it.isNotEmpty() }

    fun check(nickname: String): NicknameCheck {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return NicknameCheck.EMPTY
        if (trimmed.length > maxLength) return NicknameCheck.TOO_LONG
        val n = normalize(trimmed)
        return if (normalizedBanned.any { it in n }) NicknameCheck.BANNED else NicknameCheck.OK
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        val DEFAULT_BANNED = setOf("시발", "씨발", "병신", "개새끼", "fuck")
    }
}
