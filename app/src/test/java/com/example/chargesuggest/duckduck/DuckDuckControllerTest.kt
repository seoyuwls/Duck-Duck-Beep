package com.example.chargesuggest.duckduck

import com.example.chargesuggest.duck.DuckReactor
import com.example.chargesuggest.duck.MemoryStore
import com.example.chargesuggest.duck.domain.CarModelResolver
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.ReactionPlan
import com.example.chargesuggest.duckduck.domain.Brand
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Precip
import com.example.chargesuggest.duckduck.domain.SendResult
import com.example.chargesuggest.duckduck.domain.StopPrompt
import com.example.chargesuggest.duckduck.domain.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEC = 1_000L
private const val MIN = 60_000L

private class Rec : DuckReactor {
    val plans = mutableListOf<ReactionPlan>()
    override fun react(plan: ReactionPlan) { plans += plan }
}

class DuckDuckControllerTest {
    private val net = MockDuckDuckNetwork()
    private val rec = Rec()
    private val store = MemoryStore()
    private var navi = false
    private var now = 1_000_000_000L
    private val car = CarModelResolver.resolve("kia_sorento", null)

    private fun controller(persist: Boolean = false, sharedStore: MemoryStore = store, auto: Boolean = false, notify: Boolean = true, confirm: Boolean = false, sound: Boolean = true, roll: Float = 0.99f, tutorial: Boolean = false) = DuckDuckController(
        network = net, reactor = rec,
        persistence = if (persist) DuckDuckPersistence(sharedStore) else null,
        myCar = { car }, myBrand = { Brand.KIA }, clockMs = { now }, naviSpeaking = { navi },
        autoOpenReceived = auto, notifyNearby = notify, resultNeedsConfirm = confirm, reactionSound = sound, giftRoll = { roll }, tutorialEnabled = tutorial, freshStart = false,
    ).also { it.attach() }

    /** 주차 상태에서 온보딩을 마친다 (닉네임: 듬직한 쏘렌토) */
    private fun ready(c: DuckDuckController) {
        c.setDriveMode(DriveMode.PARKED, now)
        assertTrue(c.completeOnboarding("듬직한"))
    }

    private fun drive(c: DuckDuckController) = c.setDriveMode(DriveMode.DRIVING, now)

    /** 신호 대기: 정차가 2초 이어지게 시간을 보낸다 */
    private fun stop(c: DuckDuckController) {
        c.setDriveMode(DriveMode.STOPPED, now)
        now += DuckConfig.STOP_DWELL_MS
        c.tick(now)
    }

    // ---------------- 발견 (S2, S3) ----------------

    @Test
    fun `주행 중 같은 차를 발견하면(알림을 켠 경우) 조명과 한 줄 카드가 3초 뜬다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        val s = c.state.value
        assertTrue(s.card is Card.Found)
        assertEquals(1, rec.plans.size)
        assertEquals(DuckConfig.AMBIENT_RGB, rec.plans[0].ambientRgb)
        assertFalse(rec.plans[0].playSound) // 발견에는 소리가 없다. 소리는 주행 중 오리를 받을 때만
        assertEquals(StopPrompt.NONE, s.prompt) // 주행 중에는 버튼이 없다
        assertEquals(1, s.nearbyCount)
        assertEquals(1, s.metToday)
        now += DuckConfig.MOVING_CARD_MS
        c.tick(now)
        assertNull(c.state.value.card)
    }

    @Test
    fun `같은 종류 알림은 3분 안에 반복되지 않지만 만남은 기록된다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        now += 60 * SEC
        net.simulateFound("b")
        assertEquals(1, rec.plans.size)
        assertEquals(2, c.state.value.metToday)
        // 가장 최근에 만난 b가 제안된다
        assertEquals("b", c.state.value.suggested!!.ticket.carId)
    }

    @Test
    fun `같은 차를 15분 안에 또 감지해도 새 알림이 없다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        now += 5 * MIN
        net.simulateFound("a")
        assertEquals(1, rec.plans.size)
        assertEquals(1, c.state.value.today.size)
    }

    @Test
    fun `주차나 정차 중 발견은 소리 없이 목록과 버튼으로만 반영한다`() {
        val c = controller(); ready(c)
        net.simulateFound("a") // 주차
        assertTrue(rec.plans.isEmpty())
        assertEquals(1, c.state.value.metToday)
        assertNotNull(c.state.value.suggested)
    }

    @Test
    fun `근처 차 수는 숫자로만 세고 멀어지면 줄어든다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a"); net.simulateFound("b")
        assertEquals(2, c.state.value.nearbyCount)
        net.simulateLost("a")
        assertEquals(1, c.state.value.nearbyCount)
    }

    // ---------------- 정차와 보내기 (S4, S5) ----------------

    @Test
    fun `정차는 2초 이어져야 반영되고 그때 오리 보내기 버튼과 보낼 오리가 공개된다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        c.setDriveMode(DriveMode.STOPPED, now)
        assertEquals(DriveMode.DRIVING, c.state.value.mode)
        assertEquals(StopPrompt.NONE, c.state.value.prompt)
        assertNull(c.state.value.previewDuck)
        now += DuckConfig.STOP_DWELL_MS
        c.tick(now)
        assertEquals(DriveMode.STOPPED, c.state.value.mode)
        assertEquals(StopPrompt.SEND, c.state.value.prompt)
        assertEquals(DuckType.BASIC, c.state.value.previewDuck)
    }

    @Test
    fun `주행 중에는 오리를 보낼 수 없다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        assertEquals(SendOutcome.NotAllowedWhileMoving, c.sendSuggested(now))
        assertTrue(net.sent.isEmpty())
    }

    @Test
    fun `약한 비에 보내면 우산 오리가 가고 닉네임이 함께 간다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        c.injectWeather(WeatherSnapshot(Precip.LIGHT_RAIN, 18.0), now)
        stop(c)
        assertEquals(DuckType.UMBRELLA, c.state.value.previewDuck)
        assertEquals(SendOutcome.Sent(DuckType.UMBRELLA), c.sendSuggested(now))
        assertEquals(MockDuckDuckNetwork.Sent("a", "듬직한 쏘렌토", DuckType.UMBRELLA), net.sent.single())
        assertTrue(c.state.value.card is Card.Sent)
        assertEquals(2, rec.plans.size) // 발견 + 완료
        // 완료 카드는 1.5초 뒤 닫히고, 쓴 티켓은 더 제안되지 않는다
        now += DuckConfig.SENT_CARD_MS
        c.tick(now)
        assertNull(c.state.value.card)
        assertNull(c.state.value.suggested)
        assertEquals(StopPrompt.NONE, c.state.value.prompt)
    }

    @Test
    fun `티켓 한 장으로 두 번 보낼 수 없다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a"); stop(c)
        c.sendSuggested(now)
        assertEquals(SendOutcome.Rejected(SendResult.UNKNOWN_TICKET), c.sendSuggested(now))
        assertEquals(1, net.sent.size)
    }

    @Test
    fun `상대가 꺼져 전달하지 못하면 티켓을 소모하지 않고 다시 보낼 수 있다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a"); stop(c)
        net.online = false
        assertEquals(SendOutcome.DeliveryFailed, c.sendSuggested(now))
        assertNotNull(c.state.value.suggested)
        assertNull(c.state.value.card)
        net.online = true
        assertTrue(c.sendSuggested(now) is SendOutcome.Sent)
    }

    @Test
    fun `카드가 떠 있는 동안 멈추면 주행용 카드는 닫히고 정차용 버튼이 먼저 보인다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        net.simulateReceived(DuckType.BASIC, "A")
        assertNotNull(c.state.value.card)
        stop(c) // 카드 3초가 지나기 전에 멈춤
        assertNull(c.state.value.card)
        assertEquals(StopPrompt.SEND, c.state.value.prompt)
    }

    @Test
    fun `티켓은 15분이 지나면 보낼 수 없다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        now += 15 * MIN
        stop(c)
        assertNull(c.state.value.suggested)
        assertEquals(StopPrompt.NONE, c.state.value.prompt)
        assertEquals(SendOutcome.Rejected(SendResult.UNKNOWN_TICKET), c.sendSuggested(now))
    }

    @Test
    fun `닉네임을 정하지 않으면 보낼 수 없다`() {
        val c = controller(); c.setDriveMode(DriveMode.PARKED, now)
        drive(c); net.simulateFound("a"); stop(c)
        assertEquals(SendOutcome.NoNickname, c.sendSuggested(now))
    }

    @Test
    fun `주차 중에는 오늘의 만남 목록에서 티켓을 골라 보낼 수 있다`() {
        val c = controller(); ready(c)
        net.simulateFound("a")
        val id = c.state.value.today.single().ticket.id
        assertTrue(c.sendTicket(id, now) is SendOutcome.Sent)
        assertEquals(com.example.chargesuggest.duckduck.domain.TicketStatus.SENT, c.state.value.today.single().status)
    }

    // ---------------- 받기 (R1 ~ R6) ----------------

    @Test
    fun `주행 중 오리를 받으면 종류를 숨긴 한 줄 카드와 조명과 효과음만 나온다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.UMBRELLA, "윙크하는 쏘렌토")
        val s = c.state.value
        val card = s.card as Card.Arrived
        assertEquals(1, card.unread)
        assertNull(s.previewDuck)
        assertEquals(StopPrompt.NONE, s.prompt)
        assertEquals(1, s.unread)
        assertEquals(1, s.book.entries.size)      // 받는 순간 저장된다
        assertEquals(0, s.book.collectedCount())  // 확인 전에는 모은 오리가 아니다
        assertTrue(rec.plans.single().playSound)
    }

    @Test
    fun `정차하면 오리 확인하기 버튼이 뜨고 확인하면 새 오리 결과가 나온다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.UMBRELLA, "윙크하는 쏘렌토")
        stop(c)
        assertEquals(StopPrompt.OPEN, c.state.value.prompt)
        assertEquals("윙크하는 쏘렌토", c.state.value.nextSender)
        val opened = c.openNextDuck(now)!!
        assertTrue(opened.isNewType)
        val result = c.state.value.card as Card.Result
        assertEquals(DuckType.UMBRELLA, result.entry.type)
        assertTrue(result.isNewType)
        assertEquals(1, c.state.value.book.collectedCount())
        assertEquals(StopPrompt.NONE, c.state.value.prompt)
    }

    @Test
    fun `확인 결과는 출발하면 즉시 닫히지만 도감에는 남는다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.SCARF, "A"); stop(c)
        c.openNextDuck(now)
        assertTrue(c.state.value.card is Card.Result)
        c.setDriveMode(DriveMode.DRIVING, now)
        assertNull(c.state.value.card)
        assertEquals(1, c.state.value.book.collectedCount())
    }

    @Test
    fun `완료 카드도 출발하면 즉시 닫힌다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a"); stop(c)
        c.sendSuggested(now)
        assertTrue(c.state.value.card is Card.Sent)
        c.setDriveMode(DriveMode.DRIVING, now)
        assertNull(c.state.value.card)
    }

    @Test
    fun `주행 중에도 받은 오리를 확인할 수 있다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        assertEquals(DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING, c.openNextDuck(now) != null)
        assertEquals(if (DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING) 0 else 1, c.state.value.unread)
    }

    @Test
    fun `보낼 수 있는 티켓이 있으면 오리 보내기가 오리 확인하기보다 먼저다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        net.simulateFound("a")
        stop(c)
        assertEquals(StopPrompt.SEND, c.state.value.prompt)
        c.sendSuggested(now)
        assertEquals(StopPrompt.OPEN, c.state.value.prompt)
    }

    @Test
    fun `여러 마리를 받으면 가장 먼저 온 것부터 한 마리씩 확인한다`() {
        val c = controller(); ready(c)
        net.simulateReceived(DuckType.UMBRELLA, "A")
        now += 5 * MIN; net.simulateReceived(DuckType.BASIC, "B")
        now += 5 * MIN; net.simulateReceived(DuckType.SCARF, "C")
        assertEquals(3, c.state.value.unread)
        assertEquals(listOf("A", "B", "C"), (1..3).map { c.openNextDuck(now)!!.entry.fromNickname })
        assertEquals(0, c.state.value.unread)
        assertNull(c.openNextDuck(now))
    }

    @Test
    fun `받은 알림도 3분 안에는 반복되지 않지만 오리는 모두 저장된다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        now += 30 * SEC
        net.simulateReceived(DuckType.BASIC, "B")
        assertEquals(1, rec.plans.size)
        assertEquals(2, c.state.value.unread)
    }

    @Test
    fun `카드는 직접 닫을 수 있다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A"); stop(c)
        c.openNextDuck(now)
        c.dismissCard(now)
        assertNull(c.state.value.card)
    }

    // ---------------- 소리 정책 ----------------

    @Test
    fun `효과음 설정을 끄면 소리만 빠지고 조명과 카드는 그대로다`() {
        val c = controller(); ready(c)
        assertTrue(c.setSoundOn(false))
        drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        assertFalse(rec.plans.single().playSound)
        assertNotNull(rec.plans.single().ambientRgb)
        assertTrue(c.state.value.card is Card.Arrived)
    }

    @Test
    fun `알림 조명을 끄면 조명 없이 소리만 난다`() {
        val c = controller(); ready(c)
        assertTrue(c.setAmbientOn(false))
        drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        assertNull(rec.plans.single().ambientRgb)
        assertTrue(rec.plans.single().playSound)
    }

    @Test
    fun `조명 색을 고르면 그 색으로 한 번 비추고 이후 알림도 그 색을 쓴다`() {
        val c = controller(); ready(c)
        assertTrue(c.setAmbientRgb(0x0064FF))
        assertEquals(0x0064FF, rec.plans.last().ambientRgb)
        assertFalse(rec.plans.last().playSound)
        net.simulateReceived(DuckType.BASIC, "A")
        assertEquals(0x0064FF, rec.plans.last().ambientRgb)
    }

    @Test
    fun `조명을 꺼 두면 색을 골라도 비추지 않는다`() {
        val c = controller(); ready(c)
        c.setAmbientOn(false)
        val before = rec.plans.size
        c.setAmbientRgb(0x02C265)
        assertEquals(before, rec.plans.size)
        assertEquals(0x02C265, c.state.value.settings.ambientRgb)
    }

    @Test
    fun `닉네임 셔플은 지금과 다른 형용사로 바꾸고 쏘렌토는 그대로다`() {
        val c = controller(); ready(c)
        val before = c.state.value.nickname!!.adjective
        repeat(20) {
            val old = c.state.value.nickname!!.adjective
            val picked = c.shuffleAdjective()
            assertNotNull(picked)
            assertTrue(picked != old)
            assertEquals(picked, c.state.value.nickname!!.adjective)
            assertTrue(c.state.value.nickname!!.text.endsWith("쏘렌토"))
        }
        assertNotNull(before)
    }

    @Test
    fun `주행 중에는 닉네임 셔플과 설정 변경이 막힌다`() {
        val c = controller(); ready(c); drive(c)
        // 주행 중 설정 허용(DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING)이 꺼져 있을 때만 막힌다
        val open = DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING
        assertEquals(open, c.shuffleAdjective() != null)
        assertEquals(open, c.setSoundOn(true))
        assertEquals(open, c.setAmbientOn(false))
        assertEquals(open, c.setAmbientRgb(0xFF8A00))
    }

    // ---------------- 설정과 온보딩 (주차에서만) ----------------

    @Test
    fun `온보딩은 목록에 있는 형용사로 주차에서만 마칠 수 있다`() {
        val c = controller()
        c.setDriveMode(DriveMode.PARKED, now)
        assertFalse(c.completeOnboarding("아무말"))
        assertFalse(c.state.value.onboarded)
        drive(c)
        // 주행 중에는 닉네임을 정할 수 없다. 단, 시연용 설정(DuckConfig.ONBOARD_WHILE_DRIVING)이 켜져 있으면 가능하다
        if (!DuckConfig.ONBOARD_WHILE_DRIVING) assertFalse(c.completeOnboarding("듬직한"))
        else { val d = controller(); d.setDriveMode(DriveMode.DRIVING, now); assertTrue(d.completeOnboarding("듬직한")); return }
        c.setDriveMode(DriveMode.PARKED, now)
        assertTrue(c.completeOnboarding("듬직한"))
        val s = c.state.value
        assertTrue(s.onboarded)
        assertEquals("듬직한 쏘렌토", s.nickname!!.text)
        assertEquals("Duck Duck Beep!", s.appName)
    }

    @Test
    fun `설정은 주행 중에만 막고 정차와 주차에서는 바꿀 수 있다`() {
        val c = controller(); ready(c)
        drive(c)
        assertEquals(DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING, c.setAdjective("동글동글"))
        assertEquals(DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING, c.setSoundOn(false))
        stop(c)
        assertTrue(c.setAdjective("동글동글"))       // 정차(신호 대기)에서도 도감·설정을 열 수 있다
        assertTrue(c.setSoundOn(false))
        c.setDriveMode(DriveMode.PARKED, now)
        assertEquals("동글동글 쏘렌토", c.state.value.nickname!!.text)
    }

    @Test
    fun `온보딩은 주차에서만 마칠 수 있다 (정차에서는 안 된다)`() {
        val c = controller()
        c.setDriveMode(DriveMode.PARKED, now); drive(c); stop(c)
        assertEquals(DuckConfig.ONBOARD_WHILE_DRIVING, c.completeOnboarding("듬직한"))
    }

    @Test
    fun `오리를 받을 때 알림 설정이 켜져 있을 때만 꽥 소리가 난다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        assertTrue(rec.plans.last().playSound)
        c.setDriveMode(DriveMode.PARKED, now); c.setSoundOn(false); drive(c)
        now += DuckConfig.ALERT_MIN_INTERVAL_MS + 1
        net.simulateReceived(DuckType.BASIC, "B")
        assertFalse(rec.plans.last().playSound)
    }

    @Test
    fun `오리를 보낼 때도 꽥 소리가 나고 카드를 열 때는 나지 않는다`() {
        val c = controller(notify = false, confirm = true); ready(c)
        net.simulateFound("a")
        c.beginThrow(c.state.value.bin.ducks.first().id, now); c.confirmSend(now)
        assertTrue(rec.plans.last().playSound)
        val n = rec.plans.size
        c.onReceived(DuckType.SNOWMAN, "A", now)            // 받을 때 소리
        assertTrue(rec.plans.last().playSound)
        c.openBinDuck(c.state.value.bin.ducks.last().id, now) // 확인할 때는 소리 없음
        assertTrue(rec.plans.size > n)
        assertFalse(rec.plans.last().playSound)
    }

    // ---------------- 날씨 ----------------

    @Test
    fun `비가 그친 뒤 1시간 안에는 무지개 오리가 예고되고 그 뒤에는 기본 오리다`() {
        val c = controller(); ready(c)
        val t0 = now
        c.onWeather(Precip.HEAVY_RAIN, 20.0, t0)
        assertEquals(DuckType.RAINCOAT, c.state.value.previewDuck)
        c.onWeather(Precip.NONE, 20.0, t0 + 30 * MIN)
        assertEquals(DuckType.RAINBOW, c.state.value.previewDuck)
        now = t0 + 95 * MIN
        c.tick(now)
        assertEquals(DuckType.BASIC, c.state.value.previewDuck)
    }

    @Test
    fun `시연 시나리오 한 바퀴 - 발견 보내기 받기 확인 도감`() {
        val sender = controller(); ready(sender)
        // 1~2. 주행 중 발견
        drive(sender); net.simulateFound("other")
        assertTrue(sender.state.value.card is Card.Found)
        // 3~4. 신호 대기 + 약한 비 주입 -> 보내기
        sender.injectWeather(WeatherSnapshot(Precip.LIGHT_RAIN, 18.0), now)
        stop(sender)
        assertEquals(StopPrompt.SEND, sender.state.value.prompt)
        assertEquals(DuckType.UMBRELLA, sender.state.value.previewDuck)
        assertEquals(SendOutcome.Sent(DuckType.UMBRELLA), sender.sendSuggested(now))
        // 5~7. 받는 쪽: 주행 중 받고, 정차해서 확인하고, 주차해서 도감
        val receiverNet = MockDuckDuckNetwork()
        val receiver = DuckDuckController(receiverNet, Rec(), null, { car }, { Brand.KIA }, { now }, { false }, autoOpenReceived = false).also { it.attach() }
        receiver.setDriveMode(DriveMode.PARKED, now); receiver.completeOnboarding("새침한")
        receiver.setDriveMode(DriveMode.DRIVING, now)
        val sent = net.sent.single()
        receiverNet.simulateReceived(sent.type, sent.fromNickname)
        assertTrue(receiver.state.value.card is Card.Arrived)
        assertNull(receiver.state.value.previewDuck)
        stop(receiver)
        assertEquals("듬직한 쏘렌토", receiver.state.value.nextSender)
        receiver.openNextDuck(now)
        assertEquals(DuckType.UMBRELLA, (receiver.state.value.card as Card.Result).entry.type)
        receiver.setDriveMode(DriveMode.PARKED, now)
        assertEquals(1, receiver.state.value.book.collectedCount())
        assertEquals("듬직한 쏘렌토", receiver.state.value.book.sendersOf(DuckType.UMBRELLA).single().fromNickname)
    }


    // ---------------- 받은 오리 자동 확인 + 3초 자동 넘어가기 ----------------

    @Test
    fun `주행 중 받은 오리는 멈추는 순간 자동으로 확인 화면에 뜬다`() {
        val c = controller(auto = true); ready(c); drive(c)
        net.simulateReceived(DuckType.UMBRELLA, "윙크하는 쏘렌토")
        assertTrue(c.state.value.card is Card.Arrived)         // 주행 중에는 종류를 숨긴 알림만
        assertEquals(1, c.state.value.unread)
        stop(c)                                                 // 2초 뒤 정차로 인정되는 순간
        val r = c.state.value.card as Card.Result
        assertEquals(DuckType.UMBRELLA, r.entry.type)
        assertEquals("윙크하는 쏘렌토", r.entry.fromNickname)
        assertEquals(0, c.state.value.unread)
        assertEquals(1, c.state.value.book.collectedCount())
    }

    @Test
    fun `확인 결과는 3초 뒤 자동으로 넘어가고 다음 받은 오리가 이어서 뜬다`() {
        val c = controller(auto = true); ready(c); drive(c)
        net.simulateReceived(DuckType.UMBRELLA, "A")
        now += 4 * MIN; net.simulateReceived(DuckType.BASIC, "B")
        stop(c)
        assertEquals("A", (c.state.value.card as Card.Result).entry.fromNickname)
        now += DuckConfig.RESULT_CARD_MS - 1; c.tick(now)
        assertEquals("A", (c.state.value.card as Card.Result).entry.fromNickname)   // 아직 3초 전
        now += 1; c.tick(now)
        assertEquals("B", (c.state.value.card as Card.Result).entry.fromNickname)   // 이어서 다음 오리
        now += DuckConfig.RESULT_CARD_MS; c.tick(now)
        assertNull(c.state.value.card)                                                // 모두 확인하면 끝
        assertEquals(2, c.state.value.book.collectedCount())
    }

    @Test
    fun `정차 중에 오리를 받으면 바로 확인 화면이 뜨고 소리는 한 번만 난다`() {
        val c = controller(auto = true); ready(c); drive(c); stop(c)
        val before = rec.plans.size
        net.simulateReceived(DuckType.SCARF, "A")
        assertTrue(c.state.value.card is Card.Result)
        assertEquals(1, rec.plans.size - before)   // 도착 알림 + 확인 반응이 겹쳐 두 번 나지 않는다
    }

    @Test
    fun `주차 중에 받아도 바로 확인 화면이 뜬다`() {
        val c = controller(auto = true); ready(c)
        net.simulateReceived(DuckType.BASIC, "A")
        assertTrue(c.state.value.card is Card.Result)
    }

    @Test
    fun `주행 중에는 자동으로 확인하지 않는다`() {
        val c = controller(auto = true); ready(c); drive(c)
        net.simulateReceived(DuckType.BASIC, "A")
        now += 10 * SEC; c.tick(now)
        assertEquals(1, c.state.value.unread)
        assertNull((c.state.value.card as? Card.Result))
    }

    @Test
    fun `보내기 완료 화면이 떠 있는 동안 받은 오리는 기다렸다가 끝나면 확인한다`() {
        val c = controller(auto = true); ready(c); drive(c)
        net.simulateFound("a"); stop(c)
        c.sendSuggested(now)
        assertTrue(c.state.value.card is Card.Sent)
        net.simulateReceived(DuckType.BASIC, "A")
        assertTrue(c.state.value.card is Card.Sent)            // 완료 화면이 먼저
        now += DuckConfig.SENT_CARD_MS; c.tick(now)
        assertTrue(c.state.value.card is Card.Result)          // 끝나면 받은 오리
    }

    @Test
    fun `완료 화면과 확인 결과는 모두 3초다`() {
        assertEquals(3_000L, DuckConfig.SENT_CARD_MS)
        assertEquals(3_000L, DuckConfig.RESULT_CARD_MS)
    }

    @Test
    fun `주차 중에 보내면 완료 화면 없이 목록에서 줄만 사라진다`() {
        val c = controller(); ready(c)
        net.simulateFound("a")
        val id = c.state.value.today.single().ticket.id
        c.sendTicket(id, now)
        assertNull(c.state.value.card)
    }

    // ---------------- 저장 ----------------

    @Test
    fun `앱을 껐다 켜도 온보딩과 닉네임과 설정과 도감이 남는다`() {
        val first = controller(persist = true); ready(first)
        first.setSoundOn(false)
        first.setAmbientRgb(0x0064FF)
        first.setDriveMode(DriveMode.DRIVING, now)
        net.simulateReceived(DuckType.SCARF, "A")
        first.setDriveMode(DriveMode.PARKED, now)
        first.openNextDuck(now)
        net.simulateReceived(DuckType.BASIC, "B")  // 확인하지 않은 오리

        val second = controller(persist = true)
        val s = second.state.value
        assertTrue(s.onboarded)
        assertEquals("듬직한 쏘렌토", s.nickname!!.text)
        assertFalse(s.settings.soundOn)
        assertEquals(0x0064FF, s.settings.ambientRgb)
        assertEquals(1, s.book.collectedCount())
        assertEquals(1, s.unread)
        // 다음 받은 오리 번호가 겹치지 않는다
        second.onReceived(DuckType.BASIC, "C", now + 10 * MIN)
        assertEquals(3, second.state.value.book.entries.map { it.id }.toSet().size)
    }

    // ---------------- 오리 통 ----------------

    @Test
    fun `처음에는 오리 통이 기본 오리로만 차 있다`() {
        val c = controller()
        val bin = c.state.value.bin
        assertEquals(DuckConfig.BIN_CAPACITY, bin.size)
        assertTrue(bin.ducks.all { it.type == DuckType.BASIC })
    }

    @Test
    fun `가득 찬 통에 오리를 받으면 가장 오래된 오리가 나가고 새 오리가 들어온다`() {
        val c = controller(); ready(c)
        val oldest = c.state.value.bin.ducks.first()
        c.onReceived(DuckType.RAINCOAT, "A", now)
        val bin = c.state.value.bin
        assertEquals(DuckConfig.BIN_CAPACITY, bin.size)
        assertTrue(bin.ducks.none { it.id == oldest.id })
        assertEquals(DuckType.RAINCOAT, bin.ducks.last().type)
    }

    @Test
    fun `오리를 보내면 통에서 한 마리가 빠지고 받으면 다시 채워진다`() {
        val c = controller(); ready(c); drive(c)
        net.simulateFound("a")
        stop(c)
        assertTrue(c.sendSuggested(now) is SendOutcome.Sent)
        assertEquals(DuckConfig.BIN_CAPACITY - 1, c.state.value.bin.size)
        c.onReceived(DuckType.SNOWMAN, "B", now)
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
    }

    @Test
    fun `시작할 때마다 처음부터 시작하면 저장된 닉네임이 있어도 닉네임 정하기부터 나온다`() {
        val shared = MemoryStore()
        val a = controller(persist = true, sharedStore = shared); ready(a)
        assertTrue(a.state.value.onboarded)
        val b = DuckDuckController(
            network = net, reactor = rec, persistence = DuckDuckPersistence(shared),
            myCar = { car }, myBrand = { Brand.KIA }, clockMs = { now }, naviSpeaking = { navi }, freshStart = true,
        ).also { it.attach() }
        assertFalse(b.state.value.onboarded)
    }

    @Test
    fun `처음부터를 누르면 오리 통도 처음 상태로 돌아간다`() {
        val c = controller(); ready(c)
        c.onReceived(DuckType.SNOWMAN, "B", now)
        c.resetSaved()
        assertTrue(c.state.value.bin.ducks.all { it.type == DuckType.BASIC })
    }

    // ---------------- 던져서 보내기 (시스템은 먼저 알리지 않는다) ----------------

    @Test
    fun `근처에 같은 차가 있어도 시스템은 알리지 않는다`() {
        val c = controller(notify = false); ready(c); drive(c)
        net.simulateFound("a")
        assertNull(c.state.value.card)
        assertEquals(0, rec.plans.size)
        assertEquals(1, c.state.value.nearbyCount)
    }

    @Test
    fun `근처에 같은 차가 있을 때 오리를 던지면 그 오리가 전달되고 통에서 빠진다`() {
        val c = controller(notify = false); ready(c); drive(c)
        net.simulateFound("a")
        val duck = c.state.value.bin.ducks.first()
        assertEquals(SendOutcome.Sent(duck.type), c.throwDuck(duck.id, now))
        assertEquals(MockDuckDuckNetwork.Sent("a", "듬직한 쏘렌토", duck.type), net.sent.single())
        assertEquals(DuckConfig.BIN_CAPACITY - 1, c.state.value.bin.size)
        assertTrue(c.state.value.bin.ducks.none { it.id == duck.id })
        assertEquals(1, c.state.value.sentCount)
        assertEquals(1, rec.plans.size) // 보낸 뒤의 조명·소리 반응 한 번
    }

    @Test
    fun `던진 오리의 종류가 그대로 간다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        c.onReceived(DuckType.RAINCOAT, "B", now)
        val rain = c.state.value.bin.ducks.last()
        assertEquals(DuckType.RAINCOAT, rain.type)
        assertEquals(SendOutcome.Sent(DuckType.RAINCOAT), c.throwDuck(rain.id, now))
        assertEquals(DuckType.RAINCOAT, net.sent.single().type)
    }

    @Test
    fun `주변에 같은 차가 없으면 던져도 아무 일도 없다`() {
        val c = controller(notify = false); ready(c); drive(c)
        val duck = c.state.value.bin.ducks.first()
        assertEquals(SendOutcome.NoNearbyCar, c.throwDuck(duck.id, now))
        assertTrue(net.sent.isEmpty())
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
    }

    @Test
    fun `그 차가 멀어졌으면 던져도 전달되지 않는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a"); net.simulateLost("a")
        assertEquals(SendOutcome.NoNearbyCar, c.throwDuck(c.state.value.bin.ducks.first().id, now))
    }

    @Test
    fun `같은 차에는 하루에 한 번만 전달된다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        assertTrue(c.throwDuck(c.state.value.bin.ducks[0].id, now) is SendOutcome.Sent)
        val second = c.throwDuck(c.state.value.bin.ducks[0].id, now)
        assertTrue("두 번째는 전달되지 않는다: $second", second !is SendOutcome.Sent)
        assertEquals(1, net.sent.size)
        assertEquals(DuckConfig.BIN_CAPACITY - 1, c.state.value.bin.size)
    }

    @Test
    fun `차를 처음 만난 지 15분이 지나도 지금 근처에 있으면 전달된다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        now += DuckConfig.TICKET_VALID_MS + 60_000L
        assertTrue(c.throwDuck(c.state.value.bin.ducks[0].id, now) is SendOutcome.Sent)
    }

    @Test
    fun `상대가 꺼져 있으면 전달에 실패하고 오리는 통에 남는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        net.online = false
        assertEquals(SendOutcome.DeliveryFailed, c.throwDuck(c.state.value.bin.ducks[0].id, now))
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
        net.online = true
        assertTrue(c.throwDuck(c.state.value.bin.ducks[0].id, now) is SendOutcome.Sent) // 다시 던지면 간다
    }

    @Test
    fun `닉네임이 없거나 위치 공유를 끄면 던져도 전달되지 않는다`() {
        val c = controller(notify = false)
        c.setDriveMode(DriveMode.PARKED, now)
        net.simulateFound("a")
        assertEquals(SendOutcome.NoNickname, c.throwDuck(c.state.value.bin.ducks.first().id, now))
        assertTrue(net.sent.isEmpty())
    }

    @Test
    fun `통에 없는 오리는 던질 수 없다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        assertEquals(SendOutcome.NotInBin, c.throwDuck(9999, now))
    }

    @Test
    fun `주행 중에도 던져서 보낼 수 있다`() {
        val c = controller(notify = false); ready(c); drive(c)
        net.simulateFound("a")
        assertTrue(c.throwDuck(c.state.value.bin.ducks.first().id, now) is SendOutcome.Sent)
    }

    // ---------------- 던진 오리 "보낼까요?" 확인과 받은 오리 표시 ----------------

    @Test
    fun `근처에 같은 차가 있을 때 던진 오리는 보낼까요 확인을 띄우고 아직 보내지 않는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        val duck = c.state.value.bin.ducks.first()
        assertTrue(c.beginThrow(duck.id, now))
        assertEquals(duck.id, c.state.value.pendingSend?.binDuckId)
        assertEquals(duck.type, c.state.value.pendingSend?.type)
        assertTrue(net.sent.isEmpty())
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
    }

    @Test
    fun `보내기를 누르면 전달되고 오리를 보냈어요 카드가 3초 뜬다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        val duck = c.state.value.bin.ducks.first()
        c.beginThrow(duck.id, now)
        assertEquals(SendOutcome.Sent(duck.type), c.confirmSend(now))
        assertNull(c.state.value.pendingSend)
        assertTrue(c.state.value.card is Card.Sent)
        assertEquals(1, net.sent.size)
        assertEquals(DuckConfig.BIN_CAPACITY - 1, c.state.value.bin.size)
        now += DuckConfig.SENT_CARD_MS
        c.tick(now)
        assertNull(c.state.value.card)
    }

    @Test
    fun `돌아가기를 누르면 보내지 않고 오리는 통에 남는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        val duck = c.state.value.bin.ducks.first()
        c.beginThrow(duck.id, now)
        c.cancelSend(now)
        assertNull(c.state.value.pendingSend)
        assertTrue(net.sent.isEmpty())
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
        assertTrue(c.beginThrow(duck.id, now)) // 다시 던질 수 있다
    }

    @Test
    fun `답이 없으면 확인 카드는 10초 뒤 저절로 닫힌다`() {
        val c = controller(notify = false); ready(c); drive(c)
        net.simulateFound("a")
        c.beginThrow(c.state.value.bin.ducks.first().id, now)
        now += DuckConfig.SEND_CONFIRM_TIMEOUT_MS - 1
        c.tick(now)
        assertNotNull(c.state.value.pendingSend)
        now += 2
        c.tick(now)
        assertNull(c.state.value.pendingSend)
        assertTrue(net.sent.isEmpty())
    }

    @Test
    fun `근처에 같은 차가 없으면 던져도 확인이 뜨지 않는다`() {
        val c = controller(notify = false); ready(c)
        assertFalse(c.beginThrow(c.state.value.bin.ducks.first().id, now))
        assertNull(c.state.value.pendingSend)
    }

    @Test
    fun `이미 오늘 보낸 차뿐이면 던져도 확인이 뜨지 않는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        c.beginThrow(c.state.value.bin.ducks[0].id, now); c.confirmSend(now)
        assertFalse(c.beginThrow(c.state.value.bin.ducks[0].id, now))
    }

    @Test
    fun `확인을 기다리는 동안에는 다른 오리를 던져도 새 확인이 뜨지 않는다`() {
        val c = controller(notify = false); ready(c)
        net.simulateFound("a")
        assertTrue(c.beginThrow(c.state.value.bin.ducks[0].id, now))
        assertFalse(c.beginThrow(c.state.value.bin.ducks[1].id, now))
    }

    @Test
    fun `받은 오리는 통에 받은 오리 표시와 함께 들어오고 확인하면 표시가 사라진다`() {
        val c = controller(auto = false); ready(c)
        c.onReceived(DuckType.RAINCOAT, "A", now)
        val fresh = c.state.value.bin.ducks.last()
        assertTrue(fresh.fresh)
        assertEquals(DuckType.RAINCOAT, fresh.type)
        assertTrue(c.state.value.bin.ducks.dropLast(1).none { it.fresh })
        assertNotNull(c.openBinDuck(fresh.id, now))
        assertTrue(c.state.value.bin.ducks.none { it.fresh })
    }

    @Test
    fun `주행 중에도 받은 오리를 눌러 확인할 수 있고 확인하면 표시가 사라진다`() {
        val c = controller(auto = false); ready(c); drive(c)
        c.onReceived(DuckType.SNOWMAN, "A", now)
        val fresh = c.state.value.bin.ducks.last()
        assertNotNull(c.openBinDuck(fresh.id, now))
        assertTrue(c.state.value.bin.ducks.none { it.fresh })
        assertTrue(c.state.value.card is Card.Result)
    }

    @Test
    fun `표시가 없는 오리를 눌러도 아무 일도 없다`() {
        val c = controller(); ready(c)
        assertNull(c.openBinDuck(c.state.value.bin.ducks.first().id, now))
    }

    @Test
    fun `확인 카드는 확인을 눌러야 닫히고 그 순간 다음 받은 오리가 이어서 뜬다`() {
        val c = controller(auto = true, confirm = true); ready(c)
        c.onReceived(DuckType.SNOWMAN, "A", now)
        c.onReceived(DuckType.RAINCOAT, "B", now)
        val first = c.state.value.card as Card.Result
        assertNull(first.expiresAtMs)
        now += 60_000L
        c.tick(now)
        assertTrue("시간이 지나도 닫히지 않는다", c.state.value.card is Card.Result)
        assertEquals(DuckType.SNOWMAN, (c.state.value.card as Card.Result).entry.type)
        c.dismissCard(now)
        c.tick(now)
        assertEquals(DuckType.RAINCOAT, (c.state.value.card as Card.Result).entry.type)
        // 확인한 오리의 표시는 사라졌고 아직 확인 안 한 오리는 남아 있지 않다(둘 다 열림)
        assertTrue(c.state.value.bin.ducks.none { it.fresh })
    }

    @Test
    fun `받은 오리는 정차해도 자동으로 열리지 않고 표시된 오리를 눌러야 열린다`() {
        val c = controller(auto = false); ready(c); drive(c)
        c.onReceived(DuckType.SNOWMAN, "A", now)
        stop(c)
        assertNull("자동으로 열리지 않는다", c.state.value.card)
        assertTrue(c.state.value.bin.ducks.last().fresh)
        val opened = c.openBinDuck(c.state.value.bin.ducks.last().id, now)
        assertNotNull(opened)
        assertTrue(c.state.value.card is Card.Result)
    }

    @Test
    fun `보내기와 받기에서 효과음 설정이 꺼져 있으면 소리 없이 조명 반응만 한다`() {
        val c = controller(notify = false, sound = false); ready(c)
        net.simulateFound("a")
        c.onReceived(DuckType.BASIC, "A", now)
        c.beginThrow(c.state.value.bin.ducks.first().id, now); c.confirmSend(now)
        assertTrue(rec.plans.isNotEmpty())
        assertTrue(rec.plans.all { !it.playSound })
    }

    // ---------------- 날씨 오리 선물 (하루 한 번, 희귀) ----------------

    @Test
    fun `희귀한 날씨 조건에서 주사위가 통과하면 날씨 오리가 위에서 떨어지는 받은 오리로 들어온다`() {
        val c = controller(roll = 0.05f); ready(c)
        c.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        val duck = c.state.value.bin.ducks.last()
        assertEquals(DuckType.SNOWMAN, duck.type)
        assertTrue(duck.fresh); assertTrue(duck.gift)
        assertEquals(DuckConfig.BIN_CAPACITY, c.state.value.bin.size)
        assertEquals(1, c.state.value.book.entries.size)
        assertEquals(DuckConfig.GIFT_SENDER_LABEL, c.state.value.book.entries.single().fromNickname)
    }

    @Test
    fun `주사위가 통과하지 못하면 오리는 오지 않고 오늘의 기회도 끝난다`() {
        val c = controller(roll = 0.9f); ready(c)
        c.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        assertTrue(c.state.value.bin.ducks.all { it.type == DuckType.BASIC })
        // 같은 날 날씨가 바뀌어도 다시 굴리지 않는다
        val lucky = controller(roll = 0.0f); ready(lucky)
        lucky.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        assertTrue(lucky.state.value.bin.ducks.any { it.gift })
    }

    @Test
    fun `하루에 한 번만 굴리고 다음 날에는 다시 기회가 생긴다`() {
        var rolls = 0
        val c = DuckDuckController(
            network = net, reactor = rec, myCar = { car }, myBrand = { Brand.KIA }, clockMs = { now },
            autoOpenReceived = false, giftRoll = { rolls++; 0.0f },
        ).also { it.attach() }
        ready(c)
        c.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        c.injectWeather(WeatherSnapshot(Precip.HEAVY_RAIN, 15.0), now + 3_600_000L)
        assertEquals(1, rolls)
        assertEquals(1, c.state.value.bin.ducks.count { it.gift })
        now += 24 * 3_600_000L
        c.injectWeather(WeatherSnapshot(Precip.HEAVY_RAIN, 15.0), now)
        assertEquals(2, rolls)
        assertEquals(2, c.state.value.book.entries.size)
    }

    @Test
    fun `평범한 날씨에서는 굴리지도 않는다`() {
        var rolls = 0
        val c = DuckDuckController(
            network = net, reactor = rec, myCar = { car }, myBrand = { Brand.KIA }, clockMs = { now },
            autoOpenReceived = false, giftRoll = { rolls++; 0.0f },
        ).also { it.attach() }
        ready(c)
        c.injectWeather(WeatherSnapshot(Precip.NONE, 20.0), now)
        c.tick(now + 60_000L)
        assertEquals(0, rolls)
        // 평범한 날씨 뒤에 특별한 날씨가 오면 그때 굴린다
        c.injectWeather(WeatherSnapshot(Precip.NONE, 35.0), now + 120_000L)
        assertEquals(1, rolls)
        assertEquals(DuckType.SUNGLASSES, c.state.value.bin.ducks.last().type)
    }

    @Test
    fun `온보딩 전에는 날씨 오리를 주지 않는다`() {
        val c = controller(roll = 0.0f)
        c.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        assertTrue(c.state.value.bin.ducks.none { it.gift })
    }

    @Test
    fun `무지개 오리는 비가 그친 뒤에만 선물이 될 수 있다`() {
        val c = controller(roll = 0.2f); ready(c)
        c.injectWeather(WeatherSnapshot(Precip.NONE, 18.0, rainEndedAtMs = now - 600_000L), now)
        assertEquals(DuckType.RAINBOW, c.state.value.bin.ducks.last().type) // 무지개는 확률이 더 높다(0.35)
    }

    @Test
    fun `선물 오리를 눌러 확인하면 카드가 뜨고 표시가 사라진다`() {
        val c = controller(roll = 0.0f, confirm = true); ready(c)
        c.injectWeather(WeatherSnapshot(Precip.LIGHT_RAIN, 15.0), now)
        val duck = c.state.value.bin.ducks.last()
        val opened = c.openBinDuck(duck.id, now)
        assertNotNull(opened)
        val card = c.state.value.card as Card.Result
        assertEquals(DuckType.UMBRELLA, card.entry.type)
        assertEquals(DuckConfig.GIFT_SENDER_LABEL, card.entry.fromNickname)
        assertTrue(c.state.value.bin.ducks.none { it.fresh })
    }

    @Test
    fun `선물 기회를 쓴 날은 앱을 다시 켜도 기억된다`() {
        val shared = MemoryStore()
        val a = controller(persist = true, sharedStore = shared, roll = 0.9f); ready(a)
        a.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        val b = controller(persist = true, sharedStore = shared, roll = 0.0f)
        b.injectWeather(WeatherSnapshot(Precip.SNOW, -2.0), now)
        assertTrue("같은 날에는 다시 굴리지 않는다", b.state.value.bin.ducks.none { it.gift })
    }

    // ---------------- 시작 튜토리얼 ----------------

    @Test
    fun `닉네임을 정하면 튜토리얼 1단계가 시작된다`() {
        val c = controller(tutorial = true)
        assertEquals(0, c.state.value.tutorialStep)
        ready(c)
        assertEquals(1, c.state.value.tutorialStep)
    }

    @Test
    fun `튜토리얼을 끄면 안내 없이 바로 메인이다`() {
        val c = controller(tutorial = false); ready(c)
        assertEquals(0, c.state.value.tutorialStep)
    }

    @Test
    fun `튜토리얼 1단계에서 던져 보면 카드 없이 2단계가 되고 왼쪽에서 받은 오리가 하나 들어온다`() {
        val c = controller(tutorial = true); ready(c)
        val n = c.state.value.bin.size
        val thrown = c.state.value.bin.ducks.first().id
        assertTrue(c.beginThrow(thrown, now))                                // 근처에 차가 없어도 던져 보면 넘어간다(실제로 보내지는 않는다)
        assertTrue("던진 오리는 사라진다", c.state.value.bin.ducks.none { it.id == thrown })
        assertEquals(n - 1, c.state.value.bin.size)
        assertNull(c.state.value.pendingSend)
        assertNull(c.state.value.card)
        assertTrue(net.sent.isEmpty())
        assertEquals(2, c.state.value.tutorialStep)
        assertTrue("오리는 던진 즉시가 아니라 안내가 뜰 때 들어온다", c.state.value.bin.ducks.none { it.fresh })
        c.deliverTutorialDuck(now)
        c.deliverTutorialDuck(now)                                           // 두 번 불러도 한 마리만
        assertEquals(n, c.state.value.bin.size)
        val fresh = c.state.value.bin.ducks.last()
        assertTrue(fresh.fresh)
        assertEquals(DuckConfig.TUTORIAL_SENDER_LABEL, c.state.value.book.entries.single().fromNickname)
    }

    @Test
    fun `받은 오리를 눌러 확인하면 3단계가 되고 도감을 열면 끝난다`() {
        val c = controller(tutorial = true, confirm = true); ready(c)
        c.beginThrow(c.state.value.bin.ducks.first().id, now)
        c.deliverTutorialDuck(now)
        assertNotNull(c.openBinDuck(c.state.value.bin.ducks.last().id, now))
        assertEquals(3, c.state.value.tutorialStep)
        c.onCollectionOpened(now)
        assertEquals("도감에 다녀오면 완료 안내(4)가 잠깐 나온다", 4, c.state.value.tutorialStep)
        c.finishTutorial(now)
        assertEquals(0, c.state.value.tutorialStep)
    }

    @Test
    fun `다른 단계에서 한 행동은 튜토리얼을 넘기지 않고 건너뛰기는 언제든 된다`() {
        val c = controller(tutorial = true); ready(c)
        c.onCollectionOpened(now)               // 1단계에서 도감을 열어도 그대로
        assertEquals(1, c.state.value.tutorialStep)
        c.advanceTutorial(expected = 2, nowMs = now)
        assertEquals(1, c.state.value.tutorialStep)
        c.skipTutorial(now)
        assertEquals(0, c.state.value.tutorialStep)
    }

    @Test
    fun `다음 버튼으로도 단계를 넘길 수 있다`() {
        val c = controller(tutorial = true); ready(c)
        repeat(3) { c.advanceTutorial(nowMs = now) }
        assertEquals(4, c.state.value.tutorialStep)
        c.finishTutorial(now)
        assertEquals(0, c.state.value.tutorialStep)
    }

    @Test
    fun `튜토리얼은 첫 진입에서만 나오고 도중에 앱을 껐다 켜면 다시 나오지 않는다`() {
        val shared = MemoryStore()
        val a = controller(persist = true, sharedStore = shared, tutorial = true); ready(a)
        a.advanceTutorial(nowMs = now)
        assertEquals(2, a.state.value.tutorialStep)
        val b = controller(persist = true, sharedStore = shared, tutorial = true)
        assertEquals(0, b.state.value.tutorialStep)
        assertTrue(b.state.value.onboarded)
    }

    @Test
    fun `처음부터를 누르면 튜토리얼도 처음으로 돌아간다`() {
        val c = controller(tutorial = true); ready(c)
        c.resetSaved()
        assertEquals(0, c.state.value.tutorialStep)
        ready(c)
        assertEquals(1, c.state.value.tutorialStep)
    }
}

class DuckDuckPersistenceTest {
    private val book = com.example.chargesuggest.duckduck.domain.DuckBook()
        .receive(DuckType.UMBRELLA, "A", 10).receive(DuckType.RAINBOW, "B", 20)

    @Test
    fun `저장한 값을 그대로 불러온다`() {
        val store = MemoryStore()
        val p = DuckDuckPersistence(store)
        val opened = book.openNext()!!.book
        p.save(true, "듬직한", DuckDuckSettings(soundOn = false, ambientOn = false, ambientRgb = 0x02C265), opened)
        val s = p.load()!!
        assertTrue(s.onboarded)
        assertEquals("듬직한", s.adjective)
        assertEquals(DuckDuckSettings(soundOn = false, ambientOn = false, ambientRgb = 0x02C265), s.settings)
        assertEquals(opened, s.book)
    }

    @Test
    fun `저장된 것이 없거나 깨졌으면 처음 켠 것처럼 시작한다`() {
        val store = MemoryStore()
        val p = DuckDuckPersistence(store)
        assertNull(p.load())
        store.put("duckduck_state", "not json")
        assertNull(p.load())
    }

    @Test
    fun `목록에 없는 오리 종류는 건너뛰고 나머지는 살린다`() {
        val store = MemoryStore()
        store.put(
            "duckduck_state",
            """{"onboarded":true,"adjective":"듬직한","nextId":9,"entries":[
              {"id":1,"type":"umbrella","from":"A","at":1,"opened":true},
              {"id":2,"type":"alien","from":"B","at":2,"opened":false}]}""",
        )
        val s = DuckDuckPersistence(store).load()!!
        assertEquals(1, s.book.entries.size)
        assertEquals(DuckType.UMBRELLA, s.book.entries[0].type)
        assertEquals(9, s.book.nextId)
    }

    @Test
    fun `다음 번호는 저장된 번호가 틀려도 겹치지 않는다`() {
        val store = MemoryStore()
        store.put("duckduck_state", """{"nextId":1,"entries":[{"id":5,"type":"basic","from":"A","at":1,"opened":false}]}""")
        assertEquals(6, DuckDuckPersistence(store).load()!!.book.nextId)
    }

    @Test
    fun `지우면 저장된 값이 없어진다`() {
        val store = MemoryStore()
        val p = DuckDuckPersistence(store)
        p.save(true, "듬직한", DuckDuckSettings(), book)
        p.clear()
        assertNull(p.load())
    }
}
