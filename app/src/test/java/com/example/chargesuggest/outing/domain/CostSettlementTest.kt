package com.example.chargesuggest.outing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostSettlementTest {

    // 기본 가정: 유가 1,650원/L, 연비 12km/L, 소모품 30원/km, 감가 100원/km
    private val a = CostAssumptions()

    @Test
    fun `비용은 기름값, 소모품, 감가, 영수증 금액을 더한다`() {
        val cost = CostSettlement.breakdown(100.0, a, Expenses(tollWon = 2_000, parkingWon = 3_000))
        assertEquals(13_750, cost.fuel)
        assertEquals(3_000, cost.consumable)
        assertEquals(10_000, cost.depreciation)
        assertEquals(2_000, cost.toll)
        assertEquals(3_000, cost.parking)
        assertEquals(31_750, cost.total)
    }

    @Test
    fun `km당 단가와 적자가 없는 단가`() {
        assertEquals(267.5, a.costPerKm, 1e-9)
        assertEquals(268, CostSettlement.breakEvenPerKm(a))
    }

    @Test
    fun `A 유류비만 지원이면 나머지는 내가 부담한다`() {
        val s = CostSettlement.settle(100.0, Expenses(), a, ReimbursementPolicy.FuelOnly, Period.DAY)
        assertEquals(13_750L, s.reimbursedWon)
        assertEquals(-13_000L, s.diffWon)
        assertTrue(s.isEstimate)
    }

    @Test
    fun `C 단가 정산은 통행료와 주차비 포함 여부에 따라 달라진다`() {
        val expenses = Expenses(tollWon = 2_000, parkingWon = 3_000)

        val with = CostSettlement.settle(
            100.0, expenses, a, ReimbursementPolicy.PerKm(200, includeTollAndParking = true), Period.DAY,
        )
        assertEquals(25_000L, with.reimbursedWon)  // 100km x 200원 + 영수증 5,000원
        assertEquals(-6_750L, with.diffWon)

        val without = CostSettlement.settle(
            100.0, expenses, a, ReimbursementPolicy.PerKm(200, includeTollAndParking = false), Period.DAY,
        )
        assertEquals(20_000L, without.reimbursedWon)
        assertEquals(-11_750L, without.diffWon)
    }

    @Test
    fun `B 정액 보조금은 하루로는 비교하지 않고 월 단위로만 비교한다`() {
        val stipend = ReimbursementPolicy.MonthlyStipend(200_000)

        val day = CostSettlement.settle(50.0, Expenses(), a, stipend, Period.DAY)
        assertNull(day.reimbursedWon)
        assertNull(day.diffWon)
        assertNotNull(day.note)

        // 한 달 1,200km: 비용 321,000원, 정액 200,000원 -> 121,000원 적자
        val month = CostSettlement.settle(1_200.0, Expenses(), a, stipend, Period.MONTH)
        assertEquals(321_000L, month.myCost.total)
        assertEquals(200_000L, month.reimbursedWon)
        assertEquals(-121_000L, month.diffWon)
    }

    @Test
    fun `정액으로 적자가 나기 시작하는 월 주행거리`() {
        assertEquals(747, CostSettlement.stipendBreakEvenKm(200_000, a))
        assertNull(CostSettlement.stipendBreakEvenKm(200_000, CostAssumptions(fuelPriceWonPerL = 0, consumableWonPerKm = 0, depreciationWonPerKm = 0)))
    }

    @Test
    fun `월 시뮬레이터는 주행거리별 차이를 돌려준다`() {
        val points = CostSettlement.simulateMonth(0..1_000 step 500, a, ReimbursementPolicy.PerKm(300))
        assertEquals(listOf(0, 500, 1_000), points.map { it.km })
        assertEquals(0L, points[0].diffWon)
        // 1,000km: 비용 267,500원, 지원 300,000원 -> 32,500원 이득
        assertEquals(267_500L, points[2].myCostWon)
        assertEquals(300_000L, points[2].reimbursedWon)
        assertEquals(32_500L, points[2].diffWon)
    }
}
