package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.ReceivedDuck

/**
 * 시연용: 두 차(내 차 a, 상대 차 b)를 메모리에서 직접 잇는다.
 * a가 보내면 b가 받고, b가 보내면 a가 받는다. 서버가 생기면 DuckNetwork 구현만 바꿔 끼운다.
 */
class DuckLink(private val clockMs: () -> Long = System::currentTimeMillis) {
    private var seq = 0

    inner class End : DuckNetwork {
        private var receiver: ((ReceivedDuck) -> Unit)? = null
        internal lateinit var peer: End

        override fun setReceiver(onReceived: (ReceivedDuck) -> Unit) { receiver = onReceived }

        override fun send(targetId: String, fromNickname: String, kind: DuckKind) {
            peer.receiver?.invoke(ReceivedDuck("link-${++seq}", fromNickname, kind, clockMs()))
        }
    }

    val a = End()
    val b = End()

    init { a.peer = b; b.peer = a }
}
