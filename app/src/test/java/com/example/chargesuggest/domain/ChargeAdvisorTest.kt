package com.example.chargesuggest.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeAdvisorTest {

    @Test
    fun `배터리가 충분하면 제안하지 않는다`() {
        assertEquals(Advice.None, ChargeAdvisor().evaluate(60))
    }

    @Test
    fun `20퍼센트 이하가 되면 제안한다`() {
        assertEquals(Advice.SuggestCharge(20), ChargeAdvisor().evaluate(20))
    }

    @Test
    fun `같은 상황에서는 한 번만 제안한다`() {
        val advisor = ChargeAdvisor()
        advisor.evaluate(20)
        assertEquals(Advice.None, advisor.evaluate(19))
    }

    @Test
    fun `25퍼센트 이상으로 회복하면 다시 제안할 수 있다`() {
        val advisor = ChargeAdvisor()
        advisor.evaluate(20)
        advisor.evaluate(30)
        assertEquals(Advice.SuggestCharge(18), advisor.evaluate(18))
    }

    @Test
    fun `22퍼센트처럼 경계 사이에서는 다시 제안하지 않는다`() {
        val advisor = ChargeAdvisor()
        advisor.evaluate(20)
        advisor.evaluate(22)
        assertEquals(Advice.None, advisor.evaluate(19))
    }
}
