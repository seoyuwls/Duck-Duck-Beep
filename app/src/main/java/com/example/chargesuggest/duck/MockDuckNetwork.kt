package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.ReceivedDuck
import java.util.concurrent.atomic.AtomicInteger

/**
 * 서버가 없을 때 쓰는 목업. 시연에서 "가짜 상대 차"가 인형을 보내는 것을 흉내 낸다.
 * - send(): 보낸 기록만 남긴다
 * - simulateIncoming(): 다른 차가 인형을 보낸 것처럼 수신 콜백을 부른다
 */
class MockDuckNetwork(private val clockMs: () -> Long = System::currentTimeMillis) : DuckNetwork {

    data class Sent(val targetId: String, val fromNickname: String, val kind: DuckKind)

    private var receiver: ((ReceivedDuck) -> Unit)? = null
    private val seq = AtomicInteger(0)
    val sent = mutableListOf<Sent>()

    override fun setReceiver(onReceived: (ReceivedDuck) -> Unit) { receiver = onReceived }

    override fun send(targetId: String, fromNickname: String, kind: DuckKind) {
        sent += Sent(targetId, fromNickname, kind)
    }

    fun simulateIncoming(
        fromNickname: String = "지나가는 포니",
        kind: DuckKind = DuckCatalog.all.first(),
    ) {
        receiver?.invoke(ReceivedDuck("mock-${seq.incrementAndGet()}", fromNickname, kind, clockMs()))
    }
}
