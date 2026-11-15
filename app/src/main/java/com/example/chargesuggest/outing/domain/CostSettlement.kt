package com.example.chargesuggest.outing.domain

import kotlin.math.roundToLong

enum class VehicleOwnership {
    /** 자차 외근: 정산 안내가 필요함 */
    PERSONAL,

    /** 회사 차량: 정산 대신 보고용 기록만 필요함 */
    COMPANY,
}

/** 자차 외근에서 회사가 지원하는 방식(자주 보이는 유형) */
sealed interface ReimbursementPolicy {
    /** A. 유류비만 지원 */
    data object FuelOnly : ReimbursementPolicy

    /** B. 월 정액 보조금 */
    data class MonthlyStipend(val wonPerMonth: Long) : ReimbursementPolicy

    /** C. 실비 + km당 단가(+ 통행료·주차비 영수증) */
    data class PerKm(val wonPerKm: Int, val includeTollAndParking: Boolean = true) : ReimbursementPolicy
}

/**
 * 비용 계산에 쓰는 단가. **모두 가정값**이라 화면에서 항상 "추정"으로 표시하고 사용자가 수정할 수 있어야 한다.
 * (SDK에서 연비와 연료 잔량은 에뮬레이터에서 지원되지 않았고, 감가·소모품은 차량 정보로 알 수 없다.)
 */
data class CostAssumptions(
    val fuelPriceWonPerL: Int = 1650,
    val fuelEconomyKmPerL: Double = 12.0,
    val consumableWonPerKm: Int = 30,
    val depreciationWonPerKm: Int = 100,
) {
    /** km당 비용(기름값 + 소모품 + 감가). "km당 단가"가 정산 협상의 핵심이라 따로 보여 준다 */
    val costPerKm: Double
        get() = fuelPriceWonPerL / fuelEconomyKmPerL + consumableWonPerKm + depreciationWonPerKm
}

/** 사용자가 직접 입력하는 영수증 금액(톨비·주차비는 SDK로 알 수 없다) */
data class Expenses(val tollWon: Long = 0, val parkingWon: Long = 0)

data class CostBreakdown(
    val fuel: Long,
    val consumable: Long,
    val depreciation: Long,
    val toll: Long,
    val parking: Long,
) {
    val total: Long get() = fuel + consumable + depreciation + toll + parking
}

enum class Period { DAY, MONTH }

data class Settlement(
    val distanceKm: Double,
    val myCost: CostBreakdown,
    /** 회사가 주는 돈. 계산할 수 없으면 null(예: 정액 보조금을 하루 단위로 볼 때) */
    val reimbursedWon: Long?,
    /** 회사가 주는 돈 - 내가 쓴 돈. +이면 이득, -이면 적자. reimbursedWon이 null이면 null */
    val diffWon: Long?,
    /** 화면에 그대로 보여 줄 보충 설명 */
    val note: String? = null,
    /** 금액은 항상 추정이다 */
    val isEstimate: Boolean = true,
)

object CostSettlement {

    fun breakdown(distanceKm: Double, a: CostAssumptions, expenses: Expenses): CostBreakdown = CostBreakdown(
        fuel = (distanceKm / a.fuelEconomyKmPerL * a.fuelPriceWonPerL).roundToLong(),
        consumable = (distanceKm * a.consumableWonPerKm).roundToLong(),
        depreciation = (distanceKm * a.depreciationWonPerKm).roundToLong(),
        toll = expenses.tollWon,
        parking = expenses.parkingWon,
    )

    /**
     * 자차 외근 정산. 회사 차량(COMPANY)은 정산이 아니라 [TripSummary] 보고만 쓰므로 이 함수를 쓰지 않는다.
     */
    fun settle(
        distanceKm: Double,
        expenses: Expenses,
        assumptions: CostAssumptions,
        policy: ReimbursementPolicy,
        period: Period,
    ): Settlement {
        val cost = breakdown(distanceKm, assumptions, expenses)
        var note: String? = null
        val reimbursed: Long? = when (policy) {
            ReimbursementPolicy.FuelOnly -> cost.fuel
            is ReimbursementPolicy.PerKm -> {
                val base = (distanceKm * policy.wonPerKm).roundToLong()
                if (policy.includeTollAndParking) base + cost.toll + cost.parking else base
            }
            is ReimbursementPolicy.MonthlyStipend -> if (period == Period.MONTH) {
                policy.wonPerMonth
            } else {
                note = "정액은 월 단위로 비교해요"
                null
            }
        }
        return Settlement(
            distanceKm = distanceKm,
            myCost = cost,
            reimbursedWon = reimbursed,
            diffWon = reimbursed?.minus(cost.total),
            note = note,
        )
    }

    /** 월 주행거리 슬라이더용 한 점 */
    data class MonthPoint(val km: Int, val myCostWon: Long, val reimbursedWon: Long, val diffWon: Long)

    /** 월 주행거리를 바꿔 가며 내 비용과 회사 지원을 비교하는 시뮬레이터(톨비·주차비는 월 합계를 고정으로 넣는다) */
    fun simulateMonth(
        kmRange: IntProgression,
        assumptions: CostAssumptions,
        policy: ReimbursementPolicy,
        monthlyExpenses: Expenses = Expenses(),
    ): List<MonthPoint> = kmRange.map { km ->
        val s = settle(km.toDouble(), monthlyExpenses, assumptions, policy, Period.MONTH)
        val reimbursed = s.reimbursedWon ?: 0L
        MonthPoint(km, s.myCost.total, reimbursed, reimbursed - s.myCost.total)
    }

    /** 이 정도 km당 단가면 적자가 없다는 값(톨비·주차비 제외). 협상할 때 쓰는 숫자 */
    fun breakEvenPerKm(assumptions: CostAssumptions): Int = assumptions.costPerKm.roundToLong().toInt()

    /** 월 정액 보조금으로 적자가 나기 시작하는 월 주행거리(km). 단가가 0이면 null */
    fun stipendBreakEvenKm(stipendWon: Long, assumptions: CostAssumptions): Int? {
        val perKm = assumptions.costPerKm
        return if (perKm <= 0.0) null else (stipendWon / perKm).toInt()
    }
}
