package com.example.chargesuggest.duckduck.domain

/** 오리 종류 7가지. 보내는 사람 쪽 날씨로만 정해진다. message는 오리별 고정 문구 [팀 확인] */
enum class DuckType(val id: String, val label: String, val message: String, val hidden: Boolean = false) {
    RAINBOW("rainbow", "무지개 오리", "비 그친 길에서 왔어요", hidden = true),
    SNOWMAN("snowman", "눈사람 오리", "눈길 운전 조심하세요"),
    RAINCOAT("raincoat", "우비 오리", "장대비 속 운전 조심하세요"),
    UMBRELLA("umbrella", "우산 오리", "비 오는 날 운전 조심하세요"),
    SUNGLASSES("sunglasses", "선글라스 오리", "더운 날 시원하게 달려요"),
    SCARF("scarf", "목도리 오리", "추운 날 따뜻하게 달려요"),
    BASIC("basic", "기본 오리", "좋은 하루 되세요!"),
}

/**
 * 강수 형태. 날씨 API 응답(강수 형태 코드, 강수량)을 이 값으로 바꾸는 일은 어댑터가 한다.
 * TODO(verify): 약한 비/강한 비를 가르는 기준은 기상청 응답 값으로 정해야 한다.
 */
enum class Precip { NONE, LIGHT_RAIN, HEAVY_RAIN, SNOW }

/** rainEndedAtMs: 비가 그친 것을 처음 관측한 시각. 없으면 null */
data class WeatherSnapshot(val precip: Precip, val tempC: Double?, val rainEndedAtMs: Long? = null)

/**
 * 날씨로 오리를 정한다. 우선순위(명세 7장):
 *  1) 무지개(비가 그친 뒤 1시간 이내) 2) 눈 3) 강한 비 / 약한 비 4) 33℃ 이상 / 0℃ 이하 5) 기본
 * 날씨를 모르면(null) 기본 오리다.
 */
object WeatherDuckResolver {
    fun resolve(w: WeatherSnapshot?, nowMs: Long): DuckType {
        if (w == null) return DuckType.BASIC
        val ended = w.rainEndedAtMs
        if (w.precip == Precip.NONE && ended != null && nowMs - ended in 0..DuckConfig.RAINBOW_WINDOW_MS) {
            return DuckType.RAINBOW
        }
        return when (w.precip) {
            Precip.SNOW -> DuckType.SNOWMAN
            Precip.HEAVY_RAIN -> DuckType.RAINCOAT
            Precip.LIGHT_RAIN -> DuckType.UMBRELLA
            Precip.NONE -> when {
                w.tempC != null && w.tempC >= DuckConfig.HOT_C -> DuckType.SUNGLASSES
                w.tempC != null && w.tempC <= DuckConfig.COLD_C -> DuckType.SCARF
                else -> DuckType.BASIC
            }
        }
    }
}

/**
 * 날씨 조회 결과를 받을 때마다 "비가 그쳤는가"를 기억한다.
 * 기상청 초단기실황은 현재 값만 주므로, 이전 조회에서 비였고 이번에 아니면 그친 시각으로 기록한다.
 * 조회가 30분 간격이라 그친 시각은 최대 30분 늦을 수 있다.
 */
class WeatherTracker {
    private var lastPrecip: Precip? = null
    private var rainEndedAtMs: Long? = null

    fun observe(precip: Precip, tempC: Double?, nowMs: Long): WeatherSnapshot {
        val wasRain = lastPrecip == Precip.LIGHT_RAIN || lastPrecip == Precip.HEAVY_RAIN
        val isRain = precip == Precip.LIGHT_RAIN || precip == Precip.HEAVY_RAIN
        if (wasRain && !isRain) rainEndedAtMs = nowMs
        if (isRain) rainEndedAtMs = null // 다시 비가 오면 그친 기록은 지운다
        lastPrecip = precip
        return WeatherSnapshot(precip, tempC, rainEndedAtMs)
    }
}
