package com.example.chargesuggest.duck.nearby

import com.example.chargesuggest.duck.domain.DriveMode

/**
 * 내 차가 서버에 "나 여기 있어요"를 알리는 시점과 내용을 정한다 (docs/nearby-design.md 4.1).
 *  - 정확한 위도·경도는 보내지 않고 격자 번호만 보낸다.
 *  - 주행 중에는 자주(5초), 정차 10초, 주차 20초마다. 위치가 바뀐 격자로 옮겨 가면 곧바로(최소 2초 간격) 보낸다.
 *  - 동의하지 않았거나(enabled=false), 차종이나 위치를 모르면 아무것도 보내지 않는다.
 * 시각은 밖에서 tick(now)으로 넣어 주므로 시계 없이 시험할 수 있다.
 */
class PresenceReporter(
    private val token: String,
    private val send: (Presence) -> Unit,
) {
    /** "근처 찾기" 동의 여부. false면 아무것도 보내지 않는다 */
    var enabled = false
    var modelKey: String? = null
    var driveMode: DriveMode = DriveMode.PARKED

    private var cell: String? = null
    private var lastSentAt: Long? = null
    private var lastSentCell: String? = null

    fun onLocation(lat: Double, lon: Double) { cell = Geohash.encode(lat, lon) }

    fun tick(nowMs: Long) {
        val m = modelKey
        val c = cell
        if (!enabled || m == null || c == null) { lastSentAt = null; return } // 꺼졌다 켜지면 바로 다시 보낸다
        val last = lastSentAt
        val due = last == null ||
            nowMs - last >= intervalFor(driveMode) ||
            (c != lastSentCell && nowMs - last >= MIN_INTERVAL_MS)
        if (!due) return
        send(Presence(token, m, c, nowMs))
        lastSentAt = nowMs
        lastSentCell = c
    }

    companion object {
        const val MIN_INTERVAL_MS = 2_000L
        fun intervalFor(mode: DriveMode): Long = when (mode) {
            DriveMode.DRIVING -> 5_000L
            DriveMode.STOPPED -> 10_000L
            DriveMode.PARKED -> 20_000L   // 서버가 30초 뒤에 지우므로 그보다 짧게
        }
    }
}
