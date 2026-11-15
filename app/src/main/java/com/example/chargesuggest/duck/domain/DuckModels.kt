package com.example.chargesuggest.duck.domain

/*
 * "같은 차 인사" 앱의 핵심 데이터. SDK나 화면에 의존하지 않아서 단위 테스트로 확인할 수 있다.
 */

/** 등급. weight는 뽑힐 확률(합 100), ambientRgb는 받을 때 켜지는 앰비언트 색(0xRRGGBB) */
enum class Rarity(val label: String, val weight: Int, val ambientRgb: Int) {
    // 일반을 흰색으로 두면 앰비언트 기본값(흰색)과 같아서 켜져도 티가 나지 않는다.
    // 그래서 일반부터 눈에 띄는 색을 쓴다. 에뮬레이터 가상 콘솔의 색 이름(Cyan/Blue/Purple/White)을 기준으로 골랐다.
    COMMON("일반", 70, 0x00FFFF),      // 청록
    RARE("레어", 22, 0x0000FF),        // 파랑
    EPIC("에픽", 7, 0x800080),         // 보라
    LEGENDARY("전설", 1, 0xFFC107),    // 황금색 (TODO(verify): 차량이 임의의 RGB 값을 지원하는지)
}

/** 주는 순간의 차량 상황. 해당 상황에서만 나올 수 있는 인형이 있다 */
enum class DuckContext(val label: String) {
    RAINY("비 오는 날"),      // 와이퍼 작동
    CHARGING("충전 중"),      // 충전 포트 열림
    FAMILY("가족과 함께"),    // 뒷좌석 탑승
    NIGHT("밤"),              // 22시~새벽 5시 (시계로 판단)
}

/** 인형 종류. requires가 있으면 그 상황에서 준 인형만 나올 수 있다 */
data class DuckKind(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val requires: DuckContext? = null,
)

/** 인형 목록. 이름은 임시이며 이미지가 정해지면 바꾼다 */
object DuckCatalog {
    val all: List<DuckKind> = listOf(
        DuckKind("pony_basic", "기본 포니", Rarity.COMMON),
        DuckKind("pony_hat", "모자 포니", Rarity.RARE),
        DuckKind("pony_umbrella", "우산 포니", Rarity.RARE, DuckContext.RAINY),
        DuckKind("pony_plug", "플러그 포니", Rarity.RARE, DuckContext.CHARGING),
        DuckKind("pony_sunglasses", "선글라스 포니", Rarity.EPIC),
        DuckKind("pony_stars", "별밤 포니", Rarity.EPIC, DuckContext.NIGHT),
        DuckKind("pony_family", "나들이 포니", Rarity.EPIC, DuckContext.FAMILY),
        DuckKind("pony_crown", "왕관 포니", Rarity.LEGENDARY),
    )

    /**
     * 시연 단계에서는 기본형 포니 하나만 쓴다. 나머지 종류와 등급 구조는 코드에 남겨 두었고,
     * 이 값을 false로 바꾸면 전체 종류가 다시 켜진다.
     */
    const val DEMO_BASIC_ONLY = true

    /** 지금 실제로 쓰는 종류 (화면의 도감과 시연 뽑기가 이 목록을 쓴다) */
    val active: List<DuckKind> = if (DEMO_BASIC_ONLY) all.take(1) else all
}

/** 근처에서 발견한 같은 차. id는 익명 임시 값이며 번호판이나 위치가 아니다 */
data class NearbyCar(val id: String)

/** 받은 인형 한 개 */
data class ReceivedDuck(
    val id: String,
    val fromNickname: String,
    val kind: DuckKind,
    val receivedAtMs: Long,
)

/** 현재 주행 상태. 화면과 알림 방식이 이 값에 따라 달라진다 */
enum class DriveMode {
    DRIVING, STOPPED, PARKED;

    companion object {
        /** 기어가 P면 주차, 속도가 거의 0이면 정차, 아니면 주행 */
        fun from(isParked: Boolean, speedKmh: Float): DriveMode = when {
            isParked -> PARKED
            speedKmh < 1f -> STOPPED
            else -> DRIVING
        }
    }
}

data class DuckSettings(
    /** 끄면 주행 중에는 조명·소리·카드 없이 조용히 도감에만 쌓인다 */
    val notifyWhileDriving: Boolean = true,
)

/** 밤 시간대 여부(22시~새벽 5시). 한국 시간(UTC+9) 기준 */
fun isNight(nowMs: Long, offsetMs: Long = 9 * 3_600_000L): Boolean {
    val hour = (((nowMs + offsetMs) / 3_600_000L) % 24).toInt()
    return hour >= 22 || hour < 5
}
