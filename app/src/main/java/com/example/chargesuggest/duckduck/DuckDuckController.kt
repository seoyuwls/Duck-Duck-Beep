package com.example.chargesuggest.duckduck

import com.example.chargesuggest.duck.DuckReactor
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.MyCarModel
import com.example.chargesuggest.duck.domain.ReactionPlan
import com.example.chargesuggest.duckduck.domain.AlertKind
import com.example.chargesuggest.duckduck.domain.AlertLimiter
import com.example.chargesuggest.duckduck.domain.Brand
import com.example.chargesuggest.duckduck.domain.BookEntry
import com.example.chargesuggest.duckduck.domain.DuckBin
import com.example.chargesuggest.duckduck.domain.DuckBook
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Nickname
import com.example.chargesuggest.duckduck.domain.NicknameAdjectives
import com.example.chargesuggest.duckduck.domain.NicknameBuilder
import com.example.chargesuggest.duckduck.domain.Opened
import com.example.chargesuggest.duckduck.domain.Precip
import com.example.chargesuggest.duckduck.domain.PromptPolicy
import com.example.chargesuggest.duckduck.domain.SendResult
import com.example.chargesuggest.duckduck.domain.StopDebouncer
import com.example.chargesuggest.duckduck.domain.StopPrompt
import com.example.chargesuggest.duckduck.domain.TicketBook
import com.example.chargesuggest.duckduck.domain.TicketView
import com.example.chargesuggest.duckduck.domain.WeatherDuckResolver
import com.example.chargesuggest.duckduck.domain.WeatherSnapshot
import com.example.chargesuggest.duckduck.domain.WeatherTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DuckDuckSettings(
    /** 오리 알림: 오리를 보내고 받을 때 꽥! 소리가 나는지 */
    val soundOn: Boolean = DuckConfig.PLAY_REACTION_SOUND,
    /** 알림 조명: 오리를 보내고 받을 때 차 안 조명이 잠깐 빛나는지 */
    val ambientOn: Boolean = true,
    /** 알림 조명 색(0xRRGGBB). 설정에서 DuckConfig.AMBIENT_CHOICES 중에서 고른다 */
    val ambientRgb: Int = DuckConfig.AMBIENT_RGB,
)

/** 화면에 잠깐 뜨는 카드. expiresAtMs가 지나면 컨트롤러가 알아서 지운다(null이면 닫을 때까지) */
sealed interface Card {
    val expiresAtMs: Long?

    /** S3 주행 중 발견: "근처에 ○○를 발견했어요! 멈추면 오리를 보낼 수 있어요" */
    data class Found(override val expiresAtMs: Long) : Card

    /** S5 완료: "오리가 출발했어요" */
    data class Sent(override val expiresAtMs: Long) : Card

    /** R1 주행 중 오리 받음: "오리가 도착했어요! 멈추면 확인해볼 수 있어요" (종류는 숨김) */
    data class Arrived(override val expiresAtMs: Long, val unread: Int) : Card

    /** R4 확인 결과. 출발하면 즉시 닫힌다 */
    data class Result(val entry: BookEntry, val isNewType: Boolean, override val expiresAtMs: Long? = null) : Card
}

/** "이 오리를 보낼까요?" 확인을 기다리는 던진 오리 */
data class PendingSend(val binDuckId: Int, val type: DuckType, val expiresAtMs: Long)

sealed interface SendOutcome {
    data class Sent(val type: DuckType) : SendOutcome
    data object NotAllowedWhileMoving : SendOutcome
    data object NoNickname : SendOutcome
    data class Rejected(val result: SendResult) : SendOutcome
    data object DeliveryFailed : SendOutcome

    /** 던졌지만 근처에 같은 차가 없다(또는 오늘 보낼 수 없다): 아무 일도 일어나지 않는다 */
    data object NoNearbyCar : SendOutcome

    /** 통에 없는 오리 */
    data object NotInBin : SendOutcome
}

/** 화면이 그릴 모든 것. 화면은 이 값만 보고 그린다 */
data class DuckDuckState(
    /** 정차 디바운스까지 거친 주행 상태 */
    val mode: DriveMode = DriveMode.PARKED,
    val brand: Brand = Brand.HYUNDAI,
    /** 문구에 넣을 내 차종 이름 (예: 쏘렌토). 목록에 없으면 브랜드명 */
    val modelName: String = "",
    /** 앱 이름(Duck Duck Beep!, 브랜드와 상관없이 같다) */
    val appName: String = Brand.HYUNDAI.appName,
    val onboarded: Boolean = false,
    val nickname: Nickname? = null,
    val settings: DuckDuckSettings = DuckDuckSettings(),
    /** "근처에 N대": 숫자만 보여 주고 거리·방향은 보여 주지 않는다 */
    val nearbyCount: Int = 0,
    /** 오늘의 만남(최근 순), "오늘 만난 ○○ N대"의 N은 metToday */
    val today: List<TicketView> = emptyList(),
    val metToday: Int = 0,
    /** 지금까지 보낸 오리 수(누적) */
    val sentCount: Int = 0,
    /** 지금 시각(분 단위로 내림). 화면의 "방금 전, 17분 전" 같은 표시에 쓴다 */
    val nowMs: Long = 0,
    /** 지금 보낼 수 있는 가장 최근 티켓 */
    val suggested: TicketView? = null,
    /** "새로 온 오리 N" */
    val unread: Int = 0,
    /** 다음에 확인할 오리를 보낸 차의 닉네임 (R3 "OO 쏘렌토가 오리를 보냈어요") */
    val nextSender: String? = null,
    /** 정차 중에 보일 버튼 하나 */
    val prompt: StopPrompt = StopPrompt.NONE,
    /** S4에서 처음 공개되는 보낼 오리. 주행 중에는 null */
    val previewDuck: DuckType? = null,
    val card: Card? = null,
    val book: DuckBook = DuckBook(),
    val weather: WeatherSnapshot? = null,
    /** 메인 화면의 오리 통. 받으면 들어오고 보내면 빠진다 */
    val bin: DuckBin = DuckBin.starter(),
    /** 던져서 화면 밖으로 나간 오리의 "보낼까요?" 확인. 없으면 null */
    val pendingSend: PendingSend? = null,
    /** 처음 시작 튜토리얼 단계. 1~3이면 단계별 안내, 4면 "튜토리얼 완료" 안내(잠깐), 0이면 없음 */
    val tutorialStep: Int = 0,
)

/**
 * Duck Duck의 흐름을 관리한다 (duck-app-spec.md 5~7장). SDK에는 직접 의존하지 않는다.
 *
 * 호출은 화면(메인) 스레드에서 한다고 가정한다. 네트워크가 다른 스레드에서 알려 주면 어댑터가 메인으로 넘긴다.
 * 시간은 매번 now로 받을 수 있게 해서 테스트에서 시각을 마음대로 움직일 수 있다.
 * 화면은 0.5초 안팎으로 tick()을 불러 정차 판정(2초)과 카드 만료를 반영한다.
 */
class DuckDuckController(
    private val network: DuckDuckNetwork,
    private val reactor: DuckReactor,
    private val persistence: DuckDuckPersistence? = null,
    private val myCar: () -> MyCarModel,
    private val myBrand: () -> Brand = { Brand.HYUNDAI },
    private val clockMs: () -> Long = System::currentTimeMillis,
    /** 내비가 안내 음성을 내는 중인가 (그러면 효과음을 작게) */
    private val naviSpeaking: () -> Boolean = { false },
    private val tickets: TicketBook = TicketBook(),
    private val debouncer: StopDebouncer = StopDebouncer(),
    private val alerts: AlertLimiter = AlertLimiter(),
    private val weatherTracker: WeatherTracker = WeatherTracker(),
    /**
     * true면 정차·주차가 되는 순간(또는 그 상태에서 오리를 받는 순간) 받은 오리를 **자동으로 확인 화면에 띄운다.**
     * 확인 결과는 3초 뒤 넘어가고, 다음 받은 오리가 있으면 이어서 보여 준다.
     */
    private val autoOpenReceived: Boolean = DuckConfig.AUTO_OPEN_RECEIVED,
    /**
     * true면 근처에서 같은 차를 발견하는 순간 시스템이 알린다(소리·조명·카드). 이전 기획.
     * false(기본)면 알리지 않고 조용히 기억만 하다가, 운전자가 오리를 던졌을 때(throwDuck) 근처에 있으면 전달한다.
     */
    private val notifyNearby: Boolean = DuckConfig.NOTIFY_NEARBY_CARS,
    /** true면 받은 오리 확인 카드가 [확인]을 눌러야 닫힌다. false면 3초 뒤 자동으로 넘어간다 */
    private val resultNeedsConfirm: Boolean = DuckConfig.RESULT_NEEDS_CONFIRM,
    /** 설정의 "주행 중 알림(효과음)" 처음 값. 저장된 설정이 있으면 그것을 쓴다 */
    private val reactionSound: Boolean = DuckConfig.PLAY_REACTION_SOUND,
    /** 날씨 오리 선물의 주사위(0 이상 1 미만). 테스트에서 정해진 값을 넣는다 */
    private val giftRoll: () -> Float = { kotlin.random.Random.nextFloat() },
    /** 닉네임을 정한 직후 메인 화면에서 튜토리얼을 시작할지 */
    private val tutorialEnabled: Boolean = DuckConfig.TUTORIAL_ENABLED,
    /** 시작할 때마다 저장된 값을 지우고 처음(닉네임 정하기)부터 시작할지 */
    private val freshStart: Boolean = DuckConfig.FRESH_START_EVERY_LAUNCH,
) : DuckDuckNetworkListener {

    private var rawMode = DriveMode.PARKED
    private var mode = DriveMode.PARKED
    private var onboarded = false
    private var adjective: String? = null
    private var settings = DuckDuckSettings(soundOn = reactionSound)
    private var book = DuckBook()
    private var bin = DuckBin.starter()
    private var pending: PendingSend? = null
    private var giftDay = 0L
    private var tutorialStep = 0
    private var tutorialDuckGiven = false
    private var weather: WeatherSnapshot? = null
    private var sentCount = 0
    private var card: Card? = null
    private val nearby = LinkedHashSet<String>()

    private val _state = MutableStateFlow(DuckDuckState())
    val state: StateFlow<DuckDuckState> = _state.asStateFlow()

    /** 저장된 값을 불러오고 네트워크 수신을 시작한다 */
    fun attach() {
        if (freshStart) persistence?.clear()
        persistence?.load()?.let {
            onboarded = it.onboarded
            adjective = it.adjective?.takeIf(NicknameAdjectives::isValid)
            settings = it.settings
            book = it.book
            sentCount = it.sentCount
            giftDay = it.giftDay
            // 튜토리얼은 첫 진입에서만 나온다: 도중에 앱을 껐다 켜면 이어서 보여 주지 않고 끝난 것으로 본다
            tutorialStep = 0
        }
        network.setListener(this)
        publish(clockMs())
    }

    // ---------------- 차량 상태 ----------------

    /** 차량에서 읽은 주행 상태(기어와 주행 규제로 정한 값). 정차는 2초 이어져야 정차로 반영된다 */
    fun setDriveMode(raw: DriveMode, nowMs: Long = clockMs()) {
        rawMode = raw
        tick(nowMs)
    }

    /** 시간이 흐른 것을 반영한다: 정차 판정, 카드 만료. 화면이 주기적으로 부른다 */
    fun tick(nowMs: Long = clockMs()) {
        val effective = debouncer.update(rawMode, nowMs)
        if (effective == DriveMode.DRIVING && mode != DriveMode.DRIVING) {
            // 출발하면 정차용 카드(완료·확인 결과)는 즉시 닫는다
            if (card is Card.Sent || card is Card.Result) card = null
        }
        if (effective != DriveMode.DRIVING && mode == DriveMode.DRIVING) {
            // 멈추면 주행용 카드(발견·도착)는 닫고 정차용 버튼이 먼저 보이게 한다
            if (card is Card.Found || card is Card.Arrived) card = null
        }
        mode = effective
        maybeGiftWeatherDuck(nowMs)
        pending?.let { if (nowMs >= it.expiresAtMs) pending = null } // 답이 없으면 닫는다
        card?.expiresAtMs?.let { if (nowMs >= it) card = null }   // 만료된 카드는 여기서 먼저 지운다
        maybeAutoOpen(nowMs)
        publish(nowMs)
    }

    // ---------------- 네트워크가 알려 주는 것 ----------------

    override fun onFound(carId: String) = onFound(carId, clockMs())

    fun onFound(carId: String, nowMs: Long) {
        nearby += carId
        val ticket = tickets.onEncounter(carId, nowMs)
        // 새 만남이고 주행 중일 때만 알린다. 같은 종류 알림은 최소 간격을 둔다 (notifyNearby가 꺼져 있으면 알리지 않는다)
        if (notifyNearby && ticket != null && mode == DriveMode.DRIVING && alerts.tryAcquire(AlertKind.FOUND, nowMs)) {
            react(sound = false)
            card = Card.Found(nowMs + DuckConfig.MOVING_CARD_MS)
        }
        publish(nowMs)
    }

    override fun onLost(carId: String) {
        nearby -= carId
        publish(clockMs())
    }

    override fun onReceived(type: DuckType, fromNickname: String) = onReceived(type, fromNickname, clockMs())

    fun onReceived(type: DuckType, fromNickname: String, nowMs: Long) {
        // 오리는 받는 순간 저장한다. 확인하지 않고 출발해도 사라지지 않는다
        val entryId = book.nextId
        book = book.receive(type, fromNickname, nowMs)
        bin = bin.add(type, fresh = true, entryId = entryId).first // 꽉 차 있으면 가장 오래된 오리가 나간다. 확인할 때까지 받은 오리로 표시
        persist()
        if (canAutoOpen()) {
            // 정차·주차 중이면 바로 확인 화면을 띄운다 (확인할 때 소리·조명이 나므로 도착 알림은 따로 내지 않는다)
            maybeAutoOpen(nowMs)
        } else if (alerts.tryAcquire(AlertKind.RECEIVED, nowMs)) {
            react(sound = settings.soundOn) // 오리 알림: 받을 때 꽥!
            // 주행 중에는 종류를 숨긴 한 줄 카드만. 정차·주차에서 다른 카드가 떠 있으면 그것이 끝난 뒤 이어서 확인한다
            if (mode == DriveMode.DRIVING) card = Card.Arrived(nowMs + DuckConfig.MOVING_CARD_MS, book.unreadCount)
        }
        publish(nowMs)
    }

    // ---------------- 사용자 행동 ----------------

    /** 오리 보내기: 가장 최근에 만난 티켓 한 장으로 보낸다 */
    fun sendSuggested(nowMs: Long = clockMs()): SendOutcome {
        val t = tickets.suggested(nowMs) ?: return SendOutcome.Rejected(SendResult.UNKNOWN_TICKET)
        return sendTicket(t.id, nowMs)
    }

    /** 특정 티켓으로 오리를 보낸다 (주차 메인 목록에서 쓴다). 주행 중에는 보낼 수 없다 */
    fun sendTicket(ticketId: Int, nowMs: Long = clockMs()): SendOutcome {
        if (mode == DriveMode.DRIVING) return SendOutcome.NotAllowedWhileMoving
        val nick = nicknameOrNull() ?: return SendOutcome.NoNickname
        val ticket = tickets.ticketOf(ticketId) ?: return SendOutcome.Rejected(SendResult.UNKNOWN_TICKET)
        val result = tickets.send(ticketId, nowMs)
        if (result != SendResult.OK) return SendOutcome.Rejected(result)

        val type = WeatherDuckResolver.resolve(weather, nowMs)
        if (!network.send(ticket.carId, nick.text, type)) {
            tickets.revert(ticketId) // 전달하지 못했으니 티켓은 그대로 둔다
            publish(nowMs)
            return SendOutcome.DeliveryFailed
        }
        sentCount++
        bin = bin.sendOne(type).first
        persist()
        react(sound = settings.soundOn)
        // 완료 화면은 정차 중에만 보여 준다 (주차에서는 목록에서 줄이 사라지는 것으로 충분하고, 떠 있는 카드가 다른 일을 막지 않게)
        if (mode == DriveMode.STOPPED) card = Card.Sent(nowMs + DuckConfig.SENT_CARD_MS)
        publish(nowMs)
        return SendOutcome.Sent(type)
    }

    /**
     * 운전자가 통의 오리 한 마리를 던져서 보낸다. 시스템이 먼저 알려 주지 않으므로, 운전자가 같은 차를 보고 던진 순간에 판단한다.
     *  - 지금 근처에 같은 차가 있으면(가장 최근에 나타난 차부터) 그 오리가 전달되고 통에서 빠진다.
     *  - 없거나, 오늘 보낼 수 없거나(하루 한도, 같은 차에 하루 한 번), 전달에 실패하면 아무 일도 없다(오리는 통으로 돌아온다).
     * 주행 중에도 던질 수 있다. 던진 오리의 종류가 그대로 간다.
     */
    fun throwDuck(binDuckId: Int, nowMs: Long = clockMs()): SendOutcome {
        val duck = bin.ducks.firstOrNull { it.id == binDuckId } ?: return SendOutcome.NotInBin
        val nick = nicknameOrNull() ?: return SendOutcome.NoNickname
        if (nearby.isEmpty()) return SendOutcome.NoNearbyCar
        var last: SendOutcome = SendOutcome.NoNearbyCar
        for (carId in nearby.toList().asReversed()) {
            val ticket = tickets.availableFor(carId, nowMs)
            if (ticket == null) { last = SendOutcome.Rejected(SendResult.ALREADY_SENT); continue }
            val result = tickets.send(ticket.id, nowMs)
            if (result != SendResult.OK) { last = SendOutcome.Rejected(result); continue }
            if (!network.send(carId, nick.text, duck.type)) {
                tickets.revert(ticket.id)
                last = SendOutcome.DeliveryFailed
                continue
            }
            sentCount++
            bin = bin.remove(duck.id)
            persist()
            react(sound = settings.soundOn)
            publish(nowMs)
            return SendOutcome.Sent(duck.type)
        }
        publish(nowMs)
        return last
    }

    /**
     * 던진 오리가 화면 밖으로 나갔을 때: 지금 근처에 보낼 수 있는 같은 차가 있으면 "보낼까요?" 확인을 띄운다(true).
     * 없으면 아무 일도 없고(false) 오리는 통으로 돌아온다. 확인을 띄운다는 것 자체가 근처에 차가 있다는 뜻이므로, 던지기 전에는 알리지 않는다.
     */
    fun beginThrow(binDuckId: Int, nowMs: Long = clockMs()): Boolean {
        val duck = bin.ducks.firstOrNull { it.id == binDuckId } ?: return false
        if (pending != null) return false
        if (tutorialStep == 1) {
            // 튜토리얼 1단계: 오리를 던져 보기만 하면 2단계로 넘어간다(근처에 차가 없어도, 실제로 보내지는 않는다)
            // 던진 오리는 다시 떨어지지 않고 사라진다(통에서 빠진다). 2단계에서 받은 오리가 들어오면 다시 가득 찬다
            bin = bin.remove(duck.id)
            advanceTutorial(1, nowMs)
            return true
        }
        if (nicknameOrNull() == null || nearby.isEmpty()) return false
        val ok = nearby.toList().asReversed().any { carId ->
            val ticket = tickets.availableFor(carId, nowMs)
            ticket != null && tickets.check(ticket.id, nowMs) == SendResult.OK
        }
        if (!ok) return false
        pending = PendingSend(duck.id, duck.type, nowMs + DuckConfig.SEND_CONFIRM_TIMEOUT_MS)
        publish(nowMs)
        return true
    }

    /** [보내기]: 확인을 기다리던 오리를 보낸다. 보내졌으면 "오리를 보냈어요!"가 몇 초 뜬다 */
    fun confirmSend(nowMs: Long = clockMs()): SendOutcome {
        val p = pending ?: return SendOutcome.NotInBin
        pending = null
        val out = throwDuck(p.binDuckId, nowMs)
        if (out is SendOutcome.Sent) {
            card = Card.Sent(nowMs + DuckConfig.SENT_CARD_MS)
            publish(nowMs)
        } else publish(nowMs)
        return out
    }

    /** [돌아가기]: 보내지 않는다. 오리는 통으로 돌아온다 */
    fun cancelSend(nowMs: Long = clockMs()) {
        pending = null
        publish(nowMs)
    }

    /** 통에서 받은 오리 표시가 있는 오리를 눌렀을 때: 그 오리를 확인한다(주행 중에는 종류를 볼 수 없어 null) */
    fun openBinDuck(binDuckId: Int, nowMs: Long = clockMs()): Opened? {
        val duck = bin.ducks.firstOrNull { it.id == binDuckId && it.fresh } ?: return null
        return openDuck(duck.entryId, nowMs)
    }

    /** 오리 확인하기: 가장 먼저 온 오리부터 한 마리씩. 주행 중에는 종류를 보여 줄 수 없어 null */
    fun openNextDuck(nowMs: Long = clockMs()): Opened? = openWith(nowMs) { it.openNext() }

    /** 주차 메인 목록에서 누른 오리를 확인한다 */
    fun openDuck(entryId: Int, nowMs: Long = clockMs()): Opened? = openWith(nowMs) { it.open(entryId) }

    private fun openWith(nowMs: Long, open: (DuckBook) -> Opened?): Opened? {
        val opened = doOpen(nowMs, open) ?: return null
        publish(nowMs)
        return opened
    }

    private fun doOpen(nowMs: Long, open: (DuckBook) -> Opened?): Opened? {
        if (!PromptPolicy.canRevealDuckType(mode)) return null
        val opened = open(book) ?: return null
        book = opened.book
        persist()
        react(sound = false)
        // 확인하는 순간 통 안의 "받은 오리" 표시는 사라진다. 확인 카드는 [확인]을 눌러야 닫힌다(아니면 3초 뒤 자동으로 넘어간다)
        bin = bin.markOpened(opened.entry.id)
        card = Card.Result(opened.entry, opened.isNewType, expiresAtMs = if (resultNeedsConfirm) null else nowMs + DuckConfig.RESULT_CARD_MS)
        if (tutorialStep == 2) advanceTutorial(2, nowMs) // 튜토리얼 2단계: 받은 오리를 눌러 봤다
        return opened
    }

    // ---------------- 튜토리얼 ----------------

    /**
     * 튜토리얼을 다음 단계로 넘긴다. expected가 있으면 지금 그 단계일 때만 넘긴다(다른 단계에서 한 행동은 무시).
     *  1 오리 던져 보기 -> 2 왼쪽에서 들어온 받은 오리 눌러 보기 -> 3 도감 열어 보기 -> 끝
     */
    fun advanceTutorial(expected: Int? = null, nowMs: Long = clockMs()) {
        if (tutorialStep !in 1..3 || (expected != null && tutorialStep != expected)) return
        tutorialStep++
        if (tutorialStep > 3) tutorialStep = 4 // 3단계를 마치면 "튜토리얼 완료" 안내(4)를 잠깐 보여 준다
        persist()
        publish(nowMs)
    }

    /** 튜토리얼 완료 안내(4)를 다 보여 준 뒤 호출한다: 튜토리얼이 완전히 끝난다 */
    fun finishTutorial(nowMs: Long = clockMs()) {
        if (tutorialStep != 4) return
        tutorialStep = 0
        persist()
        publish(nowMs)
    }

    fun skipTutorial(nowMs: Long = clockMs()) {
        if (tutorialStep == 0) return
        tutorialStep = 0
        persist()
        publish(nowMs)
    }

    /** 도감을 열면 3단계가 끝난다 */
    fun onCollectionOpened(nowMs: Long = clockMs()) {
        if (tutorialStep == 3) advanceTutorial(3, nowMs)
    }

    /**
     * 튜토리얼 2단계 안내가 뜨는 순간 호출한다: 받은 오리 한 마리가 왼쪽에서 굴러 들어온다(한 번만).
     * 오리를 던진 뒤 안내 카드가 나올 때까지의 딜레이에 맞추려고, 단계가 바뀌는 즉시가 아니라 화면이 부른다.
     */
    fun deliverTutorialDuck(nowMs: Long = clockMs()) {
        if (tutorialStep != 2 || tutorialDuckGiven) return
        giveTutorialDuck(nowMs)
        publish(nowMs)
    }

    /** 2단계에서 "받은 오리는 자동으로 쌓여요"를 보여 주려고 오리 한 마리가 왼쪽에서 굴러 들어온다(조명·소리 없음) */
    private fun giveTutorialDuck(nowMs: Long) {
        tutorialDuckGiven = true
        val entryId = book.nextId
        book = book.receive(DuckType.BASIC, DuckConfig.TUTORIAL_SENDER_LABEL, nowMs)
        bin = bin.add(DuckType.BASIC, fresh = true, entryId = entryId).first
    }

    /** 정차·주차이고 떠 있는 카드가 없으면 받은 오리를 먼저 온 것부터 하나 확인 화면에 띄운다 */
    private fun canAutoOpen() = autoOpenReceived && mode != DriveMode.DRIVING && card == null && book.unreadCount > 0

    private fun maybeAutoOpen(nowMs: Long) {
        if (canAutoOpen()) doOpen(nowMs) { it.openNext() }
    }

    fun dismissCard(nowMs: Long = clockMs()) {
        card = null
        publish(nowMs)
    }

    // ---------------- 날씨 ----------------

    /**
     * 날씨 오리 선물: 하루에 한 번, 그 순간 날씨가 특별한 오리에 해당할 때(기본 오리 날씨가 아닐 때) 확률로만 통에 들어온다(희귀).
     * 기회는 해당 날씨를 처음 만난 순간에 한 번뿐이라, 성공하든 못하든 그날은 끝난다. 온보딩 전에는 주지 않는다.
     * 들어온 오리는 위에서 떨어지고 받은 오리 표시가 붙으며, 눌러서 확인하면 도감에 기록된다("오늘의 날씨 · 눈사람 오리").
     */
    private fun maybeGiftWeatherDuck(nowMs: Long) {
        if (!onboarded) return
        val day = DuckConfig.kstDay(nowMs)
        if (giftDay == day) return
        val type = WeatherDuckResolver.resolve(weather, nowMs)
        if (type == DuckType.BASIC) return
        giftDay = day
        persist()
        val chance = if (type == DuckType.RAINBOW) DuckConfig.RAINBOW_GIFT_CHANCE else DuckConfig.WEATHER_GIFT_CHANCE
        if (giftRoll() >= chance) return
        giveGift(type, nowMs)
    }

    private fun giveGift(type: DuckType, nowMs: Long) {
        val entryId = book.nextId
        book = book.receive(type, DuckConfig.GIFT_SENDER_LABEL, nowMs)
        bin = bin.add(type, fresh = true, entryId = entryId, gift = true).first
        persist()
        react(sound = settings.soundOn)
    }

    /** 시연용: 확률과 하루 한 번 제한 없이 지금 날씨의 오리를 선물로 받는다. 날씨가 평범하면(기본 오리) false */
    fun grantWeatherDuckNow(nowMs: Long = clockMs()): Boolean {
        val type = WeatherDuckResolver.resolve(weather, nowMs)
        if (type == DuckType.BASIC || !onboarded) return false
        giveGift(type, nowMs)
        publish(nowMs)
        return true
    }

    /** 날씨 조회 결과를 받았을 때. 비가 그친 시각을 추적한다 */
    fun onWeather(precip: Precip, tempC: Double?, nowMs: Long = clockMs()) {
        weather = weatherTracker.observe(precip, tempC, nowMs)
        maybeGiftWeatherDuck(nowMs)
        publish(nowMs)
    }

    /** 시연용 주입: 날씨를 통째로 정한다 */
    fun injectWeather(snapshot: WeatherSnapshot?, nowMs: Long = clockMs()) {
        weather = snapshot
        maybeGiftWeatherDuck(nowMs)
        publish(nowMs)
    }

    // ---------------- 온보딩·설정 (주차에서만) ----------------

    /** 온보딩을 마친다. 형용사는 목록에 있어야 하고 주차 중에만 할 수 있다 */
    fun completeOnboarding(adjective: String): Boolean {
        if (!(PromptPolicy.canOpenFullScreens(mode) || DuckConfig.ONBOARD_WHILE_DRIVING) || !NicknameAdjectives.isValid(adjective)) return false
        this.adjective = adjective
        onboarded = true
        if (tutorialEnabled) { tutorialStep = 1; tutorialDuckGiven = false }
        persist()
        publish(clockMs())
        return true
    }

    fun setAdjective(word: String): Boolean {
        if (!PromptPolicy.canOpenCollectionAndSettings(mode) || !NicknameAdjectives.isValid(word)) return false
        adjective = word
        persist()
        publish(clockMs())
        return true
    }

    fun setSoundOn(on: Boolean): Boolean {
        if (!PromptPolicy.canOpenCollectionAndSettings(mode)) return false
        settings = settings.copy(soundOn = on)
        persist()
        publish(clockMs())
        return true
    }

    fun setAmbientOn(on: Boolean): Boolean {
        if (!PromptPolicy.canOpenCollectionAndSettings(mode)) return false
        settings = settings.copy(ambientOn = on)
        persist()
        publish(clockMs())
        return true
    }

    /** 알림 조명 색을 바꾼다. 바로 한 번 비춰서 어떤 색인지 보여 준다(조명이 켜져 있을 때) */
    fun setAmbientRgb(rgb: Int): Boolean {
        if (!PromptPolicy.canOpenCollectionAndSettings(mode)) return false
        settings = settings.copy(ambientRgb = rgb and 0xFFFFFF)
        persist()
        if (settings.ambientOn) react(sound = false)
        publish(clockMs())
        return true
    }

    /**
     * 닉네임 셔플: 지금 것과 다른 형용사를 무작위로 고른다. 마음에 드는 것이 나올 때까지 여러 번 누른다.
     * 반환: 새 형용사(바꿀 수 없으면 null)
     */
    fun shuffleAdjective(random: kotlin.random.Random = kotlin.random.Random.Default): String? {
        if (!PromptPolicy.canOpenCollectionAndSettings(mode)) return null
        val pool = NicknameAdjectives.all.filter { it != adjective }
        val pick = pool.randomOrNull(random) ?: return null
        adjective = pick
        persist()
        publish(clockMs())
        return pick
    }

    /** 시연을 처음부터 다시 하려고 저장된 값(온보딩·닉네임·설정·도감)을 지운다 */
    fun resetSaved() {
        onboarded = false; adjective = null; settings = DuckDuckSettings(soundOn = reactionSound); book = DuckBook(); card = null; sentCount = 0; bin = DuckBin.starter(); pending = null; tutorialStep = 0
        persistence?.clear()
        publish(clockMs())
    }

    // ---------------- 안쪽 ----------------

    /** 모든 상황에서 같은 효과음 하나와 앰비언트 한 번. 내비 안내 중이면 소리만 작게 한다 */
    private fun react(sound: Boolean) {
        reactor.react(
            ReactionPlan(
                ambientRgb = if (settings.ambientOn) settings.ambientRgb else null,
                playSound = sound,
                soundVolume = if (naviSpeaking()) DuckConfig.SOUND_VOLUME_NAVI else DuckConfig.SOUND_VOLUME,
                showCard = false,
                cardMs = 0L,
            ),
        )
    }

    private fun nicknameOrNull(): Nickname? =
        adjective?.let { NicknameBuilder.build(it, myCar(), myBrand()) }

    private fun persist() = persistence?.save(onboarded, adjective, settings, book, sentCount, giftDay, tutorialStep)

    private fun publish(nowMs: Long) {
        // 만료된 카드를 먼저 지운다
        card?.expiresAtMs?.let { if (nowMs >= it) card = null }
        val brand = myBrand()
        val suggested = tickets.suggested(nowMs)
        val today = tickets.today(nowMs)
        _state.value = DuckDuckState(
            mode = mode,
            brand = brand,
            modelName = NicknameBuilder.displayModel(myCar(), brand),
            appName = brand.appName,
            onboarded = onboarded,
            nickname = nicknameOrNull(),
            settings = settings,
            nearbyCount = nearby.size,
            today = today,
            metToday = tickets.metCarsToday(nowMs),
            sentCount = sentCount,
            nowMs = nowMs / 60_000L * 60_000L,
            suggested = suggested?.let { s -> today.firstOrNull { it.ticket.id == s.id } },
            unread = book.unreadCount,
            nextSender = book.nextUnread()?.fromNickname,
            prompt = PromptPolicy.stopPrompt(mode, suggested != null, book.unreadCount),
            previewDuck = if (mode == DriveMode.DRIVING) null else WeatherDuckResolver.resolve(weather, nowMs),
            card = card,
            book = book,
            weather = weather,
            bin = bin,
            pendingSend = pending,
            tutorialStep = tutorialStep,
        )
    }
}
