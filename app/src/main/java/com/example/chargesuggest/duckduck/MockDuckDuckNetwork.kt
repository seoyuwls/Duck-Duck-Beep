package com.example.chargesuggest.duckduck

import com.example.chargesuggest.duckduck.domain.DuckType

/**
 * 서버가 없을 때 쓰는 목업. 시연에서 "가짜 상대 차"가 나타나고 오리를 보내는 것을 흉내 낸다.
 *  - send(): 보낸 기록만 남긴다. online=false면 전달에 실패한다(상대가 꺼진 경우)
 *  - simulateFound / simulateLost / simulateReceived: 서버가 알려 주는 것처럼 앱의 수신 콜백을 부른다
 */
class MockDuckDuckNetwork : DuckDuckNetwork {

    data class Sent(val targetCarId: String, val fromNickname: String, val type: DuckType)

    private var listener: DuckDuckNetworkListener? = null
    val sent = mutableListOf<Sent>()

    /** false로 두면 send가 실패한다 (상대가 꺼진 경우 시험·시연용) */
    var online = true

    override fun setListener(listener: DuckDuckNetworkListener) { this.listener = listener }

    override fun send(targetCarId: String, fromNickname: String, type: DuckType): Boolean {
        if (!online) return false
        sent += Sent(targetCarId, fromNickname, type)
        return true
    }

    fun simulateFound(carId: String) { listener?.onFound(carId) }
    fun simulateLost(carId: String) { listener?.onLost(carId) }
    fun simulateReceived(type: DuckType, fromNickname: String) { listener?.onReceived(type, fromNickname) }
}
