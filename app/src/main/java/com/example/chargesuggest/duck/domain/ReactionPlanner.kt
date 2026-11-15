package com.example.chargesuggest.duck.domain

/**
 * 인형을 받거나 줬을 때 차가 어떻게 반응할지 정한다.
 * - ambientRgb: 앰비언트 조명 색 (null이면 켜지 않음)
 * - soundVolume: 효과음 볼륨 0~1. 내비 안내 중이면 작게 해서 안내가 우선되게 한다
 * - showCard: 앱 화면 안의 카드. 앱이 화면에 보일 때만 그린다 (시스템 알림 카드는 쓸 수 없음)
 */
data class ReactionPlan(
    val ambientRgb: Int?,
    val playSound: Boolean,
    val soundVolume: Float,
    val showCard: Boolean,
    val cardMs: Long,
) {
    companion object {
        val NONE = ReactionPlan(null, false, 0f, false, 0L)
    }
}

object ReactionPlanner {
    const val CARD_MS = 2_500L
    const val NORMAL_VOLUME = 0.8f
    const val DUCKED_VOLUME = 0.3f

    /** 받았을 때 */
    fun onReceive(
        rarity: Rarity,
        mode: DriveMode,
        settings: DuckSettings,
        naviSpeaking: Boolean,
        appVisible: Boolean,
    ): ReactionPlan {
        // 주행 중 알림을 꺼 두었으면 조용히 도감에만 쌓는다
        if (mode == DriveMode.DRIVING && !settings.notifyWhileDriving) return ReactionPlan.NONE
        return ReactionPlan(
            ambientRgb = rarity.ambientRgb,
            playSound = true,
            soundVolume = if (naviSpeaking) DUCKED_VOLUME else NORMAL_VOLUME,
            showCard = appVisible,
            cardMs = CARD_MS,
        )
    }

    /** 보냈을 때: 일반 등급 색으로 짧게 반응하고 카드는 없다 */
    fun onGive(mode: DriveMode, settings: DuckSettings, naviSpeaking: Boolean): ReactionPlan {
        if (mode == DriveMode.DRIVING && !settings.notifyWhileDriving) return ReactionPlan.NONE
        return ReactionPlan(
            ambientRgb = Rarity.COMMON.ambientRgb,
            playSound = true,
            soundVolume = if (naviSpeaking) DUCKED_VOLUME else NORMAL_VOLUME,
            showCard = false,
            cardMs = 0L,
        )
    }

    /** 도감은 주행 중에는 열 수 없다 */
    fun canOpenCollection(mode: DriveMode) = mode != DriveMode.DRIVING
}
