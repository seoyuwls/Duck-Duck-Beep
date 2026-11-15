package com.example.chargesuggest.duckduck.domain

import com.example.chargesuggest.duck.domain.DriveMode

/**
 * 정차로 보려면 정차 상태가 [dwellMs] 동안 이어져야 한다 (서행·잠깐 멈춤에서 버튼이 깜빡이지 않게).
 *  - 주행·주차로 바뀌면 즉시 반영한다 (출발하면 정차용 카드는 즉시 닫아야 하므로)
 *  - 정차가 충분히 이어지기 전에는 안전한 쪽인 주행으로 보고한다
 * 입력 mode는 기존 DriveStateResolver의 결과(DRIVING = 명세의 MOVING, STOPPED = IDLING)다.
 */
class StopDebouncer(private val dwellMs: Long = DuckConfig.STOP_DWELL_MS) {
    private var stoppedSince: Long? = null

    fun update(mode: DriveMode, nowMs: Long): DriveMode {
        if (mode != DriveMode.STOPPED) {
            stoppedSince = null
            return mode
        }
        val since = stoppedSince ?: nowMs.also { stoppedSince = it }
        return if (nowMs - since >= dwellMs) DriveMode.STOPPED else DriveMode.DRIVING
    }
}

/** 정차 중에 보여 줄 버튼 하나 */
enum class StopPrompt { NONE, SEND, OPEN }

object PromptPolicy {
    /**
     * 정차에서만 버튼을 보여 준다. 겹치면 **오리 보내기가 오리 확인하기보다 먼저** (티켓은 15분 뒤 사라지지만 받은 오리는 안 사라진다).
     */
    fun stopPrompt(mode: DriveMode, hasSendableTicket: Boolean, unreadCount: Int): StopPrompt = when {
        mode != DriveMode.STOPPED -> StopPrompt.NONE
        hasSendableTicket -> StopPrompt.SEND
        unreadCount > 0 -> StopPrompt.OPEN
        else -> StopPrompt.NONE
    }

    /** 온보딩(위치 동의·닉네임)은 주차에서만 한다 */
    fun canOpenFullScreens(mode: DriveMode) = mode == DriveMode.PARKED

    /** 도감·설정은 주행 중에만 막는다(DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING이 켜져 있으면 주행 중에도 열고 바꿀 수 있다). 정차(신호 대기)와 주차에서는 열 수 있다 (팀 결정, 2026-11-13) */
    fun canOpenCollectionAndSettings(mode: DriveMode) = mode != DriveMode.DRIVING || DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING

    /** 주행 중에는 받은 오리의 종류를 보여 주지 않는다. 단, DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING이 켜져 있으면 주행 중에도 보여 준다 */
    fun canRevealDuckType(mode: DriveMode) = DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING || mode != DriveMode.DRIVING
}

enum class AlertKind { FOUND, RECEIVED }

/** 같은 종류의 알림(꽥 + 조명)이 너무 자주 나지 않게 한다 */
class AlertLimiter(private val minIntervalMs: Long = DuckConfig.ALERT_MIN_INTERVAL_MS) {
    private val last = HashMap<AlertKind, Long>()

    /** 알림을 내도 되면 true로 돌려주고 시각을 기록한다 */
    fun tryAcquire(kind: AlertKind, nowMs: Long): Boolean {
        val prev = last[kind]
        if (prev != null && nowMs - prev < minIntervalMs) return false
        last[kind] = nowMs
        return true
    }
}
