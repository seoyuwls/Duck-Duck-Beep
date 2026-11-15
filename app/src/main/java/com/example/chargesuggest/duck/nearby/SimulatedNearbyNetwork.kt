package com.example.chargesuggest.duck.nearby

import com.example.chargesuggest.duck.DuckNetwork
import com.example.chargesuggest.duck.NearbyListener
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.GivePolicy
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.ReceivedDuck
import java.util.UUID

/**
 * 서버 없이 근처 인식을 시연하는 네트워크. 가짜 차(봇) 두 대가 움직이고, 서버 역할은 SimServer가 한다.
 * 봇은 내 차 서쪽 500m에서 시속 약 72km로 동쪽으로 달려 지나간다:
 *   - "같은 차종 봇": 내 차종과 같아서 가까워지면 발견 -> 지나가면 멀어짐
 *   - "다른 차종 봇": 가까이 와도 알리지 않는다 (차종 구분을 보여 주려는 것)
 * 실제 서버를 만들면 이 클래스 대신 서버와 통신하는 DuckNetwork를 끼우면 된다.
 */
class SimulatedNearbyNetwork(
    private val clockMs: () -> Long = System::currentTimeMillis,
    val myToken: String = "me-" + UUID.randomUUID().toString().take(8),
    val server: SimServer = SimServer(
        PresenceMatcher(ttlMs = 30_000, pairCooldownMs = 20_000, clientCooldownMs = 3_000, pairGraceMs = 30_000),
        policyFactory = { GivePolicy(dailyLimit = 20, sameTargetCooldownMs = 20_000) },
    ),
) : DuckNetwork {

    class Bot(
        val token: String, val name: String, var modelKey: String,
        var lat: Double, var lon: Double,
        val eastMps: Double, val northMps: Double, val sameModel: Boolean,
    )

    private val bots = ArrayList<Bot>()
    private var nearbyListener: NearbyListener? = null
    private var lastTickMs: Long? = null
    private var round = 0
    var running = false
        private set

    /** 시뮬레이션에서 내 차가 서 있는 곳 (고정) */
    val userLat get() = USER_LAT
    val userLon get() = USER_LON

    // ---- DuckNetwork ----
    override fun setReceiver(onReceived: (ReceivedDuck) -> Unit) {} // 봇은 인형을 보내지 않는다
    override fun setNearbyListener(listener: NearbyListener) { nearbyListener = listener }
    override fun report(presence: Presence) = dispatch(server.update(presence))
    override fun send(targetId: String, fromNickname: String, kind: DuckKind) { trySend(targetId, fromNickname, kind) }

    /** 서버 검사를 거쳐 인형을 보낸다. 결과가 OK일 때만 전달된 것이다 */
    fun trySend(targetId: String, fromNickname: String, kind: DuckKind): SendResult =
        server.send(myToken, targetId, fromNickname, kind, clockMs())

    // ---- 시뮬레이션 ----
    /** 봇을 새로 출발시킨다. 내 차종이 정해지지 않았으면 봇도 맞출 수 없다 */
    fun start(userModelKey: String?) {
        round++
        bots.clear()
        val (lat, lon) = GeoMath.moveByMeters(USER_LAT, USER_LON, -START_DISTANCE_M, 0.0)
        val (lat2, lon2) = GeoMath.moveByMeters(USER_LAT, USER_LON, -START_DISTANCE_M, 30.0)
        bots += Bot("bot-same-$round", "같은 차종 봇", userModelKey ?: "unknown", lat, lon, SPEED_MPS, 0.0, sameModel = true)
        bots += Bot("bot-other-$round", "다른 차종 봇", "other-model", lat2, lon2, SPEED_MPS, 0.0, sameModel = false)
        lastTickMs = null
        running = true
    }

    fun stop() { running = false; bots.clear(); lastTickMs = null }

    /** 내 차종이 바뀌면 같은 차종 봇도 따라간다 */
    fun syncModel(userModelKey: String?) {
        bots.filter { it.sameModel }.forEach { it.modelKey = userModelKey ?: "unknown" }
    }

    /** 1초마다 불러 준다: 봇을 움직이고 서버에 알리고 오래된 정보를 지운다 */
    fun tick(nowMs: Long) {
        if (!running) return
        val dt = lastTickMs?.let { (nowMs - it) / 1000.0 } ?: 0.0
        lastTickMs = nowMs
        for (b in bots) {
            val (lat, lon) = GeoMath.moveByMeters(b.lat, b.lon, b.eastMps * dt, b.northMps * dt)
            b.lat = lat; b.lon = lon
            dispatch(server.update(Presence(b.token, b.modelKey, Geohash.encode(b.lat, b.lon), nowMs)))
        }
        dispatch(server.expire(nowMs))
    }

    /** 화면에 보여 줄 한 줄 요약: 봇별 거리와 발견 여부, 보낸 인형 수 */
    fun status(): String {
        if (!running) return "시뮬레이션 꺼짐"
        val found = server.announcedWith(myToken)
        val parts = bots.map { b ->
            val d = GeoMath.distanceM(USER_LAT, USER_LON, b.lat, b.lon).toInt()
            "${b.name} ${d}m" + if (b.token in found) " (발견)" else ""
        }
        val given = bots.sumOf { server.receivedGifts[it.token]?.size ?: 0 }
        return parts.joinToString(" · ") + (if (given > 0) " · 보낸 인형 ${given}개" else "")
    }

    private fun dispatch(events: List<NearbyEvent>) {
        for (e in events) {
            if (e.forToken != myToken) continue
            when (e) {
                is NearbyEvent.Found -> nearbyListener?.onFound(NearbyCar(e.otherToken))
                is NearbyEvent.Lost -> nearbyListener?.onLost(e.otherToken)
            }
        }
    }

    companion object {
        const val USER_LAT = 37.5665
        const val USER_LON = 126.9780
        const val START_DISTANCE_M = 500.0
        const val SPEED_MPS = 20.0
    }
}
