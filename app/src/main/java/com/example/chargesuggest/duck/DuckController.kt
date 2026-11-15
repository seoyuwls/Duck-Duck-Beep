package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckCollection
import com.example.chargesuggest.duck.domain.DuckContext
import com.example.chargesuggest.duck.domain.DuckRoller
import com.example.chargesuggest.duck.domain.DuckSettings
import com.example.chargesuggest.duck.domain.GiveCheck
import com.example.chargesuggest.duck.domain.GivePolicy
import com.example.chargesuggest.duck.domain.NearbyCar
import com.example.chargesuggest.duck.domain.NicknameCheck
import com.example.chargesuggest.duck.domain.NicknameFilter
import com.example.chargesuggest.duck.domain.ReactionPlanner
import com.example.chargesuggest.duck.domain.ReceivedDuck
import com.example.chargesuggest.duck.domain.isNight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class DuckState(
    val driveMode: DriveMode = DriveMode.PARKED,
    /** 근처에서 발견한 같은 차. 있으면 "전달하기" 버튼이 보인다 */
    val nearby: NearbyCar? = null,
    val collection: DuckCollection = DuckCollection(),
    /** 방금 받은 인형 카드. 일정 시간 뒤 화면이 dismissCard()로 지운다 */
    val card: ReceivedDuck? = null,
    val settings: DuckSettings = DuckSettings(),
    val nickname: String = "",
    val sentCount: Int = 0,
)

/**
 * 인형 주고받기의 흐름을 관리한다. SDK에는 직접 의존하지 않는다.
 * 호출은 화면(메인) 스레드에서 한다고 가정한다. 네트워크가 다른 스레드에서 알려 주면
 * 어댑터가 메인으로 넘겨서 onReceived를 부른다.
 */
class DuckController(
    private val network: DuckNetwork,
    private val reactor: DuckReactor,
    private val roller: DuckRoller = DuckRoller(),
    private val policy: GivePolicy = GivePolicy(),
    private val nicknameFilter: NicknameFilter = NicknameFilter(),
    /** 와이퍼·충전 포트·뒷좌석 같은 차량 상황. 밤은 시계로 컨트롤러가 더한다 */
    private val vehicleContext: () -> Set<DuckContext> = { emptySet() },
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val rng: Random = Random.Default,
    /** 내비가 안내 음성을 내는 중인가 (그러면 효과음을 작게) */
    private val naviSpeaking: () -> Boolean = { false },
    /** 우리 앱이 화면에 보이는가 (보일 때만 카드를 그린다) */
    private val appVisible: () -> Boolean = { true },
    /** 있으면 닉네임·도감·설정을 저장하고, 시작할 때 불러온다 */
    private val persistence: DuckPersistence? = null,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DuckState> = _state.asStateFlow()

    private fun initialState(): DuckState {
        val saved = persistence?.load() ?: return DuckState()
        return DuckState(
            collection = DuckCollection(saved.items),
            nickname = saved.nickname,
            settings = DuckSettings(notifyWhileDriving = saved.notifyWhileDriving),
            sentCount = saved.sentCount,
        )
    }

    /** 저장할 값이 바뀐 뒤에 부른다 */
    private fun persist() { persistence?.save(_state.value) }

    /** 도감과 보낸 개수를 비운다 (시연을 처음부터 다시 할 때). 닉네임과 설정은 남긴다 */
    fun clearCollection() {
        _state.update { it.copy(collection = DuckCollection(), sentCount = 0, card = null) }
        persist()
    }

    fun attach() {
        network.setReceiver(::onReceived)
        // 서버가 근처 같은 차를 알려 주면 "같은 차를 발견했어요"가 뜨고, 멀어지면 사라진다
        network.setNearbyListener(object : NearbyListener {
            override fun onFound(car: NearbyCar) = setNearby(car)
            override fun onLost(carId: String) { if (_state.value.nearby?.id == carId) setNearby(null) }
        })
    }

    fun setDriveMode(mode: DriveMode) = _state.update { it.copy(driveMode = mode) }

    /** 근처 같은 차가 생기거나(null이면 사라짐) 바뀔 때 */
    fun setNearby(car: NearbyCar?) = _state.update { it.copy(nearby = car) }

    fun setNotifyWhileDriving(on: Boolean) {
        _state.update { it.copy(settings = it.settings.copy(notifyWhileDriving = on)) }
        persist()
    }

    /** 닉네임을 바꾼다. 통과하지 못하면 바꾸지 않고 이유를 돌려준다 */
    fun setNickname(text: String): NicknameCheck {
        val check = nicknameFilter.check(text)
        if (check == NicknameCheck.OK) {
            _state.update { it.copy(nickname = text.trim()) }
            persist()
        }
        return check
    }

    /** "전달하기": 랜덤 인형을 뽑아 근처 차에 보낸다 */
    fun give(): GiveCheck {
        val s = _state.value
        val target = s.nearby ?: return GiveCheck.NO_TARGET
        if (s.nickname.isBlank()) return GiveCheck.NO_TARGET // 닉네임을 정해야 보낼 수 있다
        val now = clockMs()
        val check = policy.check(target.id, now)
        if (check != GiveCheck.ALLOWED) return check

        val contexts = vehicleContext() + if (isNight(now)) setOf(DuckContext.NIGHT) else emptySet()
        val kind = roller.roll(contexts, rng)
        network.send(target.id, s.nickname, kind)
        policy.record(target.id, now)
        _state.update { it.copy(sentCount = it.sentCount + 1) }
        persist()
        reactor.react(ReactionPlanner.onGive(s.driveMode, s.settings, naviSpeaking()))
        return GiveCheck.ALLOWED
    }

    /** 인형이 도착했을 때: 도감에 쌓고, 차가 반응하고, 카드를 띄운다 */
    fun onReceived(duck: ReceivedDuck) {
        val s = _state.value
        val plan = ReactionPlanner.onReceive(duck.kind.rarity, s.driveMode, s.settings, naviSpeaking(), appVisible())
        _state.update {
            it.copy(
                collection = it.collection.add(duck),
                card = if (plan.showCard) duck else it.card,
            )
        }
        persist()
        reactor.react(plan)
    }

    fun dismissCard() = _state.update { it.copy(card = null) }

    fun canOpenCollection() = ReactionPlanner.canOpenCollection(_state.value.driveMode)
}
