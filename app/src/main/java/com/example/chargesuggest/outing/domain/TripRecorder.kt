package com.example.chargesuggest.outing.domain

enum class DistanceSource {
    /** 계기판 주행거리의 차이 (가장 믿을 수 있음) */
    ODOMETER,

    /** 내비가 알려 준 구간 거리 (계기판 값을 못 받았을 때 대신 사용) */
    NAVIGATION,

    /** 둘 다 없음 */
    UNKNOWN,
}

data class VisitRecord(
    val stopName: String,
    val arrivedMin: Int,
    /** 직전 위치에서 여기까지의 거리(km) */
    val legDistanceKm: Double,
    val legDurationMin: Int,
    val source: DistanceSource,
    /** 도착 후 사용자가 한 번 탭해서 고르는 방문 목적 */
    val purpose: String? = null,
)

data class TripSummary(
    val visits: Int,
    val totalKm: Double,
    val totalMinutes: Int,
    /** 계기판이 아닌 값이 섞여 있으면 true */
    val hasEstimates: Boolean,
)

/**
 * 이동 중 조용한 자동 기록. 방문지에 도착할 때마다 계기판 주행거리와 시각을 받아
 * 구간별 거리와 시간을 남긴다(거리는 계기판 값의 차이로 계산한다. SDK의 여정 거리는 지원되지 않았다).
 */
class TripRecorder {
    private val records = mutableListOf<VisitRecord>()
    private var lastOdometerKm: Float? = null
    private var lastMin: Int = 0

    /** 출발 시점 기록 */
    fun start(odometerKm: Float?, minOfDay: Int) {
        lastOdometerKm = odometerKm
        lastMin = minOfDay
    }

    /**
     * 방문지 도착 기록.
     * @param odometerKm 계기판 주행거리. 받지 못했으면 null
     * @param navDistanceM 내비의 구간 거리(대체용)
     */
    fun onArrive(stopName: String, odometerKm: Float?, navDistanceM: Int?, minOfDay: Int): VisitRecord {
        val prev = lastOdometerKm
        val odoDiff = if (odometerKm != null && prev != null) odometerKm - prev else null

        val (km, source) = when {
            odoDiff != null && odoDiff > 0f -> odoDiff.toDouble() to DistanceSource.ODOMETER
            navDistanceM != null && navDistanceM > 0 -> navDistanceM / 1000.0 to DistanceSource.NAVIGATION
            else -> 0.0 to DistanceSource.UNKNOWN
        }
        val record = VisitRecord(
            stopName = stopName,
            arrivedMin = minOfDay,
            legDistanceKm = km,
            legDurationMin = (minOfDay - lastMin).coerceAtLeast(0),
            source = source,
        )
        records += record
        if (odometerKm != null) lastOdometerKm = odometerKm
        lastMin = minOfDay
        return record
    }

    /** 도착 후 방문 목적을 한 번 탭으로 기록 */
    fun setPurpose(index: Int, purpose: String) {
        records[index] = records[index].copy(purpose = purpose)
    }

    fun visits(): List<VisitRecord> = records.toList()

    fun summary(): TripSummary = TripSummary(
        visits = records.size,
        totalKm = records.sumOf { it.legDistanceKm },
        totalMinutes = records.sumOf { it.legDurationMin },
        hasEstimates = records.any { it.source != DistanceSource.ODOMETER },
    )
}
