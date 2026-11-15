package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.ReactionPlan
import com.example.chargesuggest.duck.domain.ReceivedDuck

/** 차의 반응(앰비언트 조명 + 효과음). 실제 구현은 pleos/PleosDuckReactor */
interface DuckReactor {
    fun react(plan: ReactionPlan)
}

/**
 * 실제 차량의 주행 상태(주차/정차/주행)를 알려 주는 통로. 실제 구현은 pleos/PleosDriveStateSource.
 * 값이 바뀔 때만 메인 스레드에서 onChanged가 불린다.
 */
interface DriveStateSource {
    fun start(onChanged: (com.example.chargesuggest.duck.domain.DriveMode) -> Unit)
    fun stop()
}

/**
 * 차대번호를 읽는 통로. 전체 차대번호는 읽는 쪽에서만 잠깐 다루고, 로그에 남기거나 저장하지 않는다.
 * 읽지 못하면 null.
 */
interface VinSource {
    fun read(onResult: (String?) -> Unit)
}

/** 차량의 모델명을 읽는 통로. 실제 구현은 pleos/AaosCarInfoSource. 읽지 못하면 null */
interface ModelNameSource {
    fun read(onResult: (com.example.chargesuggest.duck.domain.CarNameInfo?) -> Unit)
}

/** 작은 설정값 저장소 (앱을 껐다 켜도 남는 값). 실제 구현은 SharedPrefsStore */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * 인형을 주고받는 통로. 실제로는 서버가 근처의 같은 차종을 이어 준다.
 * 지금은 목업(MockDuckNetwork)으로 대신한다.
 */
interface DuckNetwork {
    /** 인형이 도착하면 불린다. 불리는 스레드는 구현에 따라 다르다 */
    fun setReceiver(onReceived: (ReceivedDuck) -> Unit)

    fun send(targetId: String, fromNickname: String, kind: DuckKind)

    /** 근처 같은 차의 발견·멀어짐을 알려 줄 곳. 근처 인식을 하지 않는 구현은 아무것도 안 해도 된다 */
    fun setNearbyListener(listener: NearbyListener) {}

    /** "나 여기 있어요"를 서버에 알린다 (정확한 위치가 아니라 격자 번호만 담긴다). 근처 인식을 안 하는 구현은 무시한다 */
    fun report(presence: com.example.chargesuggest.duck.nearby.Presence) {}
}

/** 근처 같은 차의 발견·멀어짐 알림을 받는 쪽 */
interface NearbyListener {
    fun onFound(car: com.example.chargesuggest.duck.domain.NearbyCar)
    fun onLost(carId: String)
}
