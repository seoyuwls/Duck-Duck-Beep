package com.example.chargesuggest.duckduck

import com.example.chargesuggest.duckduck.domain.DuckType

/** 서버(또는 목업)가 앱에 알려 주는 것. 불리는 스레드는 구현에 따라 다르므로 구현이 메인 스레드로 넘겨서 부른다 */
interface DuckDuckNetworkListener {
    /** 근처에서 같은 차종을 발견했다. carId는 그 만남에서만 쓰는 익명 값이다 */
    fun onFound(carId: String)

    /** 그 차가 멀어졌거나 앱을 껐다 */
    fun onLost(carId: String)

    /** 오리가 도착했다. 보낸 차의 닉네임과 오리 종류가 함께 온다 */
    fun onReceived(type: DuckType, fromNickname: String)
}

/**
 * 오리를 주고받는 통로. 실제 구현은 서버가 근처의 같은 차종을 짝지어 준다(docs/nearby-design.md).
 * 지금은 목업(MockDuckDuckNetwork)이나 앱 안 시뮬레이션으로 대신한다.
 */
interface DuckDuckNetwork {
    fun setListener(listener: DuckDuckNetworkListener)

    /**
     * 오리를 보낸다. 서버가 전달하지 못하면(상대가 꺼짐, 만남이 만료됨, 네트워크 오류) false.
     * false면 앱은 티켓을 소모하지 않은 것으로 되돌린다.
     */
    fun send(targetCarId: String, fromNickname: String, type: DuckType): Boolean
}
