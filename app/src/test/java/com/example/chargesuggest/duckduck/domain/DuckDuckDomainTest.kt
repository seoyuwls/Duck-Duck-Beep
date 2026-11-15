package com.example.chargesuggest.duckduck.domain

import com.example.chargesuggest.duck.domain.CarModelCatalog
import com.example.chargesuggest.duck.domain.CarModelResolver
import com.example.chargesuggest.duck.domain.CarNameInfo
import com.example.chargesuggest.duck.domain.DriveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val MIN = 60_000L

class WeatherDuckTest {
    private val now = 10_000_000_000L
    private fun w(p: Precip, t: Double? = 20.0, ended: Long? = null) = WeatherSnapshot(p, t, ended)
    private fun resolve(s: WeatherSnapshot?) = WeatherDuckResolver.resolve(s, now)

    @Test fun `날씨를 모르면 기본 오리`() = assertEquals(DuckType.BASIC, resolve(null))
    @Test fun `약한 비는 우산 오리`() = assertEquals(DuckType.UMBRELLA, resolve(w(Precip.LIGHT_RAIN)))
    @Test fun `강한 비는 우비 오리`() = assertEquals(DuckType.RAINCOAT, resolve(w(Precip.HEAVY_RAIN)))
    @Test fun `눈은 눈사람 오리`() = assertEquals(DuckType.SNOWMAN, resolve(w(Precip.SNOW, -3.0)))
    @Test fun `33도 이상이면 선글라스 오리`() = assertEquals(DuckType.SUNGLASSES, resolve(w(Precip.NONE, 33.0)))
    @Test fun `0도 이하이면 목도리 오리`() = assertEquals(DuckType.SCARF, resolve(w(Precip.NONE, 0.0)))
    @Test fun `평범한 날은 기본 오리`() = assertEquals(DuckType.BASIC, resolve(w(Precip.NONE, 20.0)))
    @Test fun `기온을 모르면 기본 오리`() = assertEquals(DuckType.BASIC, resolve(w(Precip.NONE, null)))

    @Test
    fun `눈이 오면 추워도 눈사람 오리가 목도리보다 먼저다`() =
        assertEquals(DuckType.SNOWMAN, resolve(w(Precip.SNOW, -10.0)))

    @Test
    fun `비가 오면 더워도 비 오리가 선글라스보다 먼저다`() =
        assertEquals(DuckType.UMBRELLA, resolve(w(Precip.LIGHT_RAIN, 35.0)))

    @Test
    fun `비가 그친 지 1시간 안이면 무지개 오리가 가장 먼저다`() {
        assertEquals(DuckType.RAINBOW, resolve(w(Precip.NONE, 35.0, ended = now - 30 * MIN)))
        assertEquals(DuckType.RAINBOW, resolve(w(Precip.NONE, 20.0, ended = now - 60 * MIN)))
    }

    @Test
    fun `1시간이 지났거나 다시 비가 오면 무지개 오리가 아니다`() {
        assertEquals(DuckType.BASIC, resolve(w(Precip.NONE, 20.0, ended = now - 61 * MIN)))
        assertEquals(DuckType.UMBRELLA, resolve(w(Precip.LIGHT_RAIN, 20.0, ended = now - 10 * MIN)))
    }

    @Test fun `무지개 오리만 히든이다`() =
        assertEquals(listOf(DuckType.RAINBOW), DuckType.values().filter { it.hidden })

    @Test fun `오리는 7종이다`() = assertEquals(7, DuckType.values().size)

    @Test
    fun `추적기는 비에서 비 아님으로 바뀐 시각을 그친 시각으로 기록한다`() {
        val t = WeatherTracker()
        assertNull(t.observe(Precip.NONE, 20.0, 1_000).rainEndedAtMs)        // 계속 맑음
        assertNull(t.observe(Precip.LIGHT_RAIN, 18.0, 2_000).rainEndedAtMs)  // 비
        assertEquals(3_000L, t.observe(Precip.NONE, 18.0, 3_000).rainEndedAtMs) // 그침
        assertEquals(3_000L, t.observe(Precip.NONE, 18.0, 4_000).rainEndedAtMs) // 계속 유지
        assertNull(t.observe(Precip.HEAVY_RAIN, 18.0, 5_000).rainEndedAtMs)  // 다시 비: 지움
    }

    @Test
    fun `추적기 결과로 무지개 오리가 정해진다`() {
        val t = WeatherTracker()
        t.observe(Precip.HEAVY_RAIN, 20.0, 0)
        val snap = t.observe(Precip.NONE, 20.0, 30 * MIN)
        assertEquals(DuckType.RAINBOW, WeatherDuckResolver.resolve(snap, 40 * MIN))
        assertEquals(DuckType.BASIC, WeatherDuckResolver.resolve(snap, 100 * MIN))
    }
}

class NicknameTest {
    @Test
    fun `형용사는 7개 묶음의 목록에서만 고른다`() {
        assertTrue(NicknameAdjectives.isValid("듬직한"))
        assertFalse(NicknameAdjectives.isValid("아무말"))
        assertFalse(NicknameAdjectives.isValid(null))
        assertEquals(7, AdjectiveGroup.values().size)
    }

    @Test
    fun `기본값은 생김새와 기분 묶음에서만 뽑는다`() {
        val allowed = AdjectiveGroup.LOOK.words + AdjectiveGroup.MOOD.words
        val rng = Random(1)
        repeat(500) { assertTrue(NicknameAdjectives.randomDefault(rng) in allowed) }
    }

    @Test
    fun `형용사 중복이 없다`() = assertEquals(NicknameAdjectives.all.size, NicknameAdjectives.all.toSet().size)

    @Test
    fun `제조사 이름으로 브랜드를 정하고 읽지 못하면 현대다`() {
        assertEquals(Brand.GENESIS, Brand.fromMake("Genesis"))
        assertEquals(Brand.KIA, Brand.fromMake("KIA Motors"))
        assertEquals(Brand.HYUNDAI, Brand.fromMake("Hyundai"))
        assertEquals(Brand.HYUNDAI, Brand.fromMake(null))
        assertEquals("Duck Duck Beep!", Brand.KIA.appName) // 앱 이름은 브랜드와 상관없이 같다
        assertEquals(Brand.HYUNDAI.appName, Brand.GENESIS.appName)
    }

    @Test
    fun `목록에 있는 차종은 브랜드를 뺀 이름으로 닉네임을 만든다`() {
        val model = CarModelResolver.resolve("kia_sorento", null)
        assertEquals("듬직한 쏘렌토", NicknameBuilder.build("듬직한", model, Brand.KIA).text)
        val ioniq = CarModelResolver.resolve(null, null, nameInfo = CarNameInfo("Hyundai", "IONIQ 5", null))
        assertEquals("새침한 아이오닉 5", NicknameBuilder.build("새침한", ioniq, Brand.HYUNDAI).text)
    }

    @Test
    fun `차종 이름을 모르면 브랜드명으로 대신한다`() {
        val unknown = CarModelResolver.resolve(null, null, nameInfo = CarNameInfo("Kia", "Some New Model", null))
        assertEquals("듬직한 기아", NicknameBuilder.build("듬직한", unknown, Brand.KIA).text)
        val none = CarModelResolver.resolve(null, null)
        assertEquals("듬직한 현대", NicknameBuilder.build("듬직한", none, Brand.HYUNDAI).text)
    }

    @Test
    fun `가장 긴 형용사와 가장 긴 차종명을 붙여도 카드 한 줄에 들어간다`() {
        val longestAdj = NicknameAdjectives.all.maxByOrNull { it.length }!!
        val longestModel = CarModelCatalog.all.map { it.label.substringAfter(' ') }.maxByOrNull { it.length }!!
        val text = Nickname(longestAdj, longestModel).text
        // 카드 한 줄 기준(제안): 글자 수 16자 이하
        assertTrue("너무 긺: $text (${text.length}자)", text.length <= 16)
    }

    @Test
    fun `조사 이 가를 받침에 맞춰 붙인다`() {
        assertEquals("쏘렌토가", Josa.subject("쏘렌토"))
        assertEquals("그랜저가", Josa.subject("그랜저"))
        assertEquals("투싼이", Josa.subject("투싼"))
        assertEquals("니로가", Josa.subject("니로"))
        assertEquals("아이오닉 5가", Josa.subject("아이오닉 5"))
        assertEquals("EV6이", Josa.subject("EV6"))
        assertEquals("EV9가", Josa.subject("EV9"))
        assertEquals("GV80이", Josa.subject("GV80"))
        assertEquals("기아가", Josa.subject("기아"))
        assertEquals("현대가", Josa.subject("현대"))
    }

    @Test
    fun `조사 을 를을 받침에 맞춰 붙인다`() {
        assertEquals("쏘렌토를", Josa.obj("쏘렌토"))
        assertEquals("투싼을", Josa.obj("투싼"))
        assertEquals("아이오닉 5를", Josa.obj("아이오닉 5"))
        assertEquals("GV80을", Josa.obj("GV80"))
    }
}

class TicketBookTest {
    private val day = 86_400_000L
    private fun book(limit: Int = 20, perRecipient: Int = 1) =
        TicketBook(dailyLimit = limit, perRecipientPerDay = perRecipient, dayOf = { it / day })

    @Test
    fun `처음 만나면 티켓이 생기고 15분 안에 또 감지돼도 같은 만남이다`() {
        val b = book()
        assertNotNull(b.onEncounter("a", 0))
        assertNull(b.onEncounter("a", 5 * MIN))
        assertNull(b.onEncounter("a", 14 * MIN))
        assertEquals(1, b.today(14 * MIN).size)
    }

    @Test
    fun `15분이 지나 다시 만나면 새 티켓이 생긴다`() {
        val b = book()
        b.onEncounter("a", 0)
        assertNotNull(b.onEncounter("a", 15 * MIN))
        assertEquals(2, b.today(15 * MIN).size)
        assertEquals(1, b.metCarsToday(15 * MIN)) // 같은 차는 한 대로 센다
    }

    @Test
    fun `티켓은 만남부터 15분이 지나면 만료되고 남은 시간이 줄어든다`() {
        val b = book()
        val t = b.onEncounter("a", 0)!!
        assertEquals(TicketStatus.AVAILABLE, b.today(0)[0].status)
        assertEquals(10 * MIN, b.today(5 * MIN)[0].remainingMs)
        assertEquals(TicketStatus.EXPIRED, b.today(15 * MIN)[0].status)
        assertEquals(0L, b.today(20 * MIN)[0].remainingMs)
        assertEquals(SendResult.EXPIRED, b.send(t.id, 15 * MIN))
    }

    @Test
    fun `티켓 한 장으로 한 번만 보낼 수 있다`() {
        val b = book()
        val t = b.onEncounter("a", 0)!!
        assertEquals(SendResult.OK, b.send(t.id, 1 * MIN))
        assertEquals(SendResult.ALREADY_SENT, b.send(t.id, 2 * MIN))
        assertEquals(TicketStatus.SENT, b.today(3 * MIN)[0].status)
        assertNull(b.suggested(3 * MIN)) // 보낸 티켓은 더 제안하지 않는다
    }

    @Test
    fun `여러 장이면 가장 최근에 만난 한 장만 제안한다`() {
        val b = book()
        b.onEncounter("a", 0)
        val tb = b.onEncounter("b", 3 * MIN)!!
        b.onEncounter("c", 1 * MIN)
        assertEquals(tb.id, b.suggested(4 * MIN)!!.id)
        // 가장 최근 티켓이 만료되면 남은 유효한 것 중 가장 최근
        val remaining = b.suggested(17 * MIN) // a(0분, 만료) b(3분→18분까지 유효) c(1분→16분 만료)
        assertEquals(tb.id, remaining!!.id)
        assertNull(b.suggested(19 * MIN))
    }

    @Test
    fun `하루 보내기 제한을 넘기면 막고 다음 날에는 풀린다`() {
        val b = book(limit = 2)
        val ids = listOf("a", "b", "c").map { b.onEncounter(it, 0)!!.id }
        assertEquals(SendResult.OK, b.send(ids[0], 1 * MIN))
        assertEquals(SendResult.OK, b.send(ids[1], 1 * MIN))
        assertEquals(SendResult.DAILY_LIMIT, b.send(ids[2], 1 * MIN))
        assertEquals(2, b.sentToday(1 * MIN))
        val next = b.onEncounter("d", day + MIN)!!
        assertEquals(SendResult.OK, b.send(next.id, day + 2 * MIN))
    }

    @Test
    fun `같은 상대에게는 하루에 한 번만 보낼 수 있다`() {
        val b = book()
        val t1 = b.onEncounter("a", 0)!!
        assertEquals(SendResult.OK, b.send(t1.id, 1 * MIN))
        val t2 = b.onEncounter("a", 20 * MIN)!! // 15분 뒤 새 만남
        assertEquals(SendResult.RECIPIENT_LIMIT, b.send(t2.id, 21 * MIN))
        // 다른 상대는 보낼 수 있다
        val other = b.onEncounter("b", 22 * MIN)!!
        assertEquals(SendResult.OK, b.send(other.id, 23 * MIN))
    }

    @Test
    fun `없는 티켓은 보낼 수 없다`() = assertEquals(SendResult.UNKNOWN_TICKET, book().send(99, 0))

    @Test
    fun `오늘의 만남 목록은 최근 순이고 어제 것은 빠진다`() {
        val b = book()
        b.onEncounter("old", 0)
        b.onEncounter("a", day + 1 * MIN)
        b.onEncounter("b", day + 2 * MIN)
        val list = b.today(day + 3 * MIN)
        assertEquals(listOf("b", "a"), list.map { it.ticket.carId })
    }
}

class DuckBookTest {
    private val t0 = 1_000L

    @Test
    fun `받은 오리는 바로 저장되지만 확인 전에는 모은 오리로 세지 않는다`() {
        val book = DuckBook().receive(DuckType.UMBRELLA, "듬직한 쏘렌토", t0)
        assertEquals(1, book.unreadCount)
        assertEquals(0, book.collectedCount())
        assertEquals(1, book.entries.size)
    }

    @Test
    fun `가장 먼저 온 오리부터 한 마리씩 확인한다`() {
        var book = DuckBook()
            .receive(DuckType.UMBRELLA, "A", 1).receive(DuckType.BASIC, "B", 2).receive(DuckType.SCARF, "C", 3)
        assertEquals(3, book.unreadCount)
        val o1 = book.openNext()!!; book = o1.book
        assertEquals("A", o1.entry.fromNickname)
        assertEquals(2, book.unreadCount)
        val o2 = book.openNext()!!; book = o2.book
        assertEquals("B", o2.entry.fromNickname)
        assertEquals(2, book.collectedCount())
        assertNull(DuckBook().openNext())
    }

    @Test
    fun `처음 받은 종류만 새 오리이고 같은 종류를 또 받으면 아니다`() {
        var book = DuckBook().receive(DuckType.UMBRELLA, "A", 1).receive(DuckType.UMBRELLA, "B", 2)
        val first = book.openNext()!!; book = first.book
        assertTrue(first.isNewType)
        val second = book.openNext()!!; book = second.book
        assertFalse(second.isNewType)
        assertEquals(2, book.countOf(DuckType.UMBRELLA))
        assertEquals(1, book.collectedCount())
    }

    @Test
    fun `보낸 차 목록은 확인한 것만 최근 순으로 보여 준다`() {
        var book = DuckBook().receive(DuckType.BASIC, "A", 10).receive(DuckType.BASIC, "B", 20).receive(DuckType.BASIC, "C", 30)
        book = book.openNext()!!.book.openNext()!!.book // C는 아직 안 열었다
        assertEquals(listOf("B", "A"), book.sendersOf(DuckType.BASIC).map { it.fromNickname })
    }

    @Test
    fun `도감은 7종이고 모두 모으면 7 중 7이다`() {
        var book = DuckBook()
        DuckType.values().forEachIndexed { i, t -> book = book.receive(t, "n$i", i.toLong()) }
        repeat(7) { book = book.openNext()!!.book }
        assertEquals(7, book.totalTypes)
        assertEquals(7, book.collectedCount())
    }
}

class StopPolicyTest {
    @Test
    fun `정차는 2초 이어져야 정차로 보고 그 전에는 주행으로 본다`() {
        val d = StopDebouncer(dwellMs = 2_000)
        assertEquals(DriveMode.DRIVING, d.update(DriveMode.STOPPED, 0))
        assertEquals(DriveMode.DRIVING, d.update(DriveMode.STOPPED, 1_999))
        assertEquals(DriveMode.STOPPED, d.update(DriveMode.STOPPED, 2_000))
    }

    @Test
    fun `출발하면 즉시 주행으로 돌아가고 다시 멈추면 처음부터 센다`() {
        val d = StopDebouncer(dwellMs = 2_000)
        d.update(DriveMode.STOPPED, 0)
        assertEquals(DriveMode.STOPPED, d.update(DriveMode.STOPPED, 2_500))
        assertEquals(DriveMode.DRIVING, d.update(DriveMode.DRIVING, 2_600))
        assertEquals(DriveMode.DRIVING, d.update(DriveMode.STOPPED, 3_000))   // 다시 멈춤, 시작
        assertEquals(DriveMode.DRIVING, d.update(DriveMode.STOPPED, 4_500))   // 1.5초
        assertEquals(DriveMode.STOPPED, d.update(DriveMode.STOPPED, 5_000))
    }

    @Test
    fun `주차는 기다리지 않고 바로 주차다`() {
        val d = StopDebouncer()
        assertEquals(DriveMode.PARKED, d.update(DriveMode.PARKED, 0))
    }

    @Test
    fun `버튼은 정차에서만 보이고 보내기가 확인하기보다 먼저다`() {
        assertEquals(StopPrompt.SEND, PromptPolicy.stopPrompt(DriveMode.STOPPED, true, 2))
        assertEquals(StopPrompt.OPEN, PromptPolicy.stopPrompt(DriveMode.STOPPED, false, 2))
        assertEquals(StopPrompt.NONE, PromptPolicy.stopPrompt(DriveMode.STOPPED, false, 0))
        assertEquals(StopPrompt.NONE, PromptPolicy.stopPrompt(DriveMode.DRIVING, true, 5))
        assertEquals(StopPrompt.NONE, PromptPolicy.stopPrompt(DriveMode.PARKED, true, 5))
    }

    @Test
    fun `도감과 설정은 주행 중에만 막고 온보딩은 주차에서만 하며 오리 종류 공개는 설정을 따른다`() {
        assertTrue(PromptPolicy.canOpenCollectionAndSettings(DriveMode.PARKED))
        assertTrue(PromptPolicy.canOpenCollectionAndSettings(DriveMode.STOPPED))
        assertEquals(DuckConfig.COLLECTION_SETTINGS_WHILE_DRIVING, PromptPolicy.canOpenCollectionAndSettings(DriveMode.DRIVING))
        assertTrue(PromptPolicy.canOpenFullScreens(DriveMode.PARKED))
        assertFalse(PromptPolicy.canOpenFullScreens(DriveMode.STOPPED))
        assertFalse(PromptPolicy.canOpenFullScreens(DriveMode.DRIVING))
        // 주행 중에도 받은 오리를 확인할 수 있다(DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING). 꺼 두면 주행 중에는 숨긴다
        assertEquals(DuckConfig.REVEAL_RECEIVED_WHILE_DRIVING, PromptPolicy.canRevealDuckType(DriveMode.DRIVING))
        assertTrue(PromptPolicy.canRevealDuckType(DriveMode.STOPPED))
        assertTrue(PromptPolicy.canRevealDuckType(DriveMode.PARKED))
    }

    @Test
    fun `같은 종류의 알림은 최소 간격 안에 반복되지 않고 종류가 다르면 따로 센다`() {
        val l = AlertLimiter(minIntervalMs = 3 * MIN)
        assertTrue(l.tryAcquire(AlertKind.FOUND, 0))
        assertFalse(l.tryAcquire(AlertKind.FOUND, 2 * MIN))
        assertTrue(l.tryAcquire(AlertKind.RECEIVED, 2 * MIN))
        assertTrue(l.tryAcquire(AlertKind.FOUND, 3 * MIN))
    }
}
