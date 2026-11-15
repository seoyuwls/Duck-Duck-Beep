package com.example.chargesuggest.outing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingComposerTest {

    private val composer = BriefingComposer(charsPerSecond = 5.0)
    private val stop = Stop("s1", "강남 고객사", Point(37.4979, 127.0276), appointmentMin = hm(14, 0))
    private val info = VisitInfo(
        counterpart = "김 팀장",
        agenda = listOf("단가 재협의", "납기 일정"),
        lastContact = "견적서 회신 대기 중이에요",
        prepNote = "견적서 사본",
    )

    @Test
    fun `남은 시간에 비례하되 12초에서 45초 사이로 제한한다`() {
        assertEquals(12, composer.budgetSeconds(1))
        assertEquals(24, composer.budgetSeconds(2))
        assertEquals(45, composer.budgetSeconds(20))
    }

    @Test
    fun `시간이 넉넉하면 안건, 지난 연락, 준비물까지 모두 말한다`() {
        val long = composer.compose(stop, info, minutesToArrival = 20)
        assertTrue(long.text.contains("김 팀장"))
        assertTrue(long.text.contains("오후 2시"))
        assertTrue(long.text.contains("단가 재협의"))
        assertTrue(long.text.contains("납기 일정"))
        assertTrue(long.text.contains("견적서 회신 대기 중"))
        assertTrue(long.text.contains("견적서 사본"))
    }

    @Test
    fun `시간이 촉박하면 머리말과 핵심 안건 위주로 줄인다`() {
        val long = composer.compose(stop, info, minutesToArrival = 20)
        val short = composer.compose(stop, info, minutesToArrival = 1)

        assertTrue(short.sentences.size < long.sentences.size)
        assertTrue(short.sentences.first().startsWith("강남 고객사, 김 팀장님과"))
        assertTrue(short.text.contains("단가 재협의"))
        assertFalse(short.text.contains("견적서"))
        assertTrue(short.estimatedSeconds <= 12)
    }

    @Test
    fun `예산이 아주 작아도 머리말은 항상 말한다`() {
        val tiny = BriefingComposer(charsPerSecond = 0.1).compose(stop, info, minutesToArrival = 1)
        assertEquals(1, tiny.sentences.size)
        assertTrue(tiny.text.contains("강남 고객사"))
    }

    @Test
    fun `약속 시각이 없으면 방문이라고만 말한다`() {
        val free = stop.copy(appointmentMin = null)
        val briefing = composer.compose(free, info, minutesToArrival = 20)
        assertTrue(briefing.sentences.first().endsWith("방문이에요."))
        assertFalse(briefing.text.contains("약속"))
    }
}
