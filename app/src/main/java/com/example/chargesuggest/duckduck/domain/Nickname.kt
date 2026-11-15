package com.example.chargesuggest.duckduck.domain

import com.example.chargesuggest.duck.domain.ModelSource
import com.example.chargesuggest.duck.domain.MyCarModel
import kotlin.random.Random

/** 닉네임 형용사 7개 묶음 (명세 8장). 자유 입력은 없고 목록에서만 고른다 */
enum class AdjectiveGroup(val label: String, val words: List<String>) {
    GESTURE("몸짓", listOf("인사하는", "윙크하는", "깜빡깜빡")),
    LOOK("생김새", listOf("동글동글", "각진", "날렵한", "듬직한", "아담한", "늠름한")),
    SKIN("피부", listOf("반짝이는", "뽀득뽀득", "반질반질", "흙먼지 묻은", "무광")),
    CONDITION("컨디션", listOf("따끈따끈", "배부른", "조용조용")),
    MOOD("기분", listOf("신난", "설레는", "의젓한", "새침한", "느긋한")),
    JOURNEY("지나온 길", listOf("여행 다녀온", "바다 본", "산 넘은", "오래 달린", "첫 나들이")),
    SEASON("계절", listOf("꽃잎 붙은", "낙엽 붙은", "눈 쌓인")),
}

object NicknameAdjectives {
    val all: List<String> = AdjectiveGroup.values().flatMap { it.words }

    fun isValid(word: String?) = word != null && word in all

    /** 처음 미리 선택해 둘 값. 생김새·기분 묶음에서만 뽑는다 */
    fun randomDefault(rng: Random = Random.Default): String =
        (AdjectiveGroup.LOOK.words + AdjectiveGroup.MOOD.words).random(rng)
}

/** 제조사 브랜드. 앱 안에서는 "Duck Duck 브랜드"로 완성해서 보여 준다 */
enum class Brand(val appName: String, val korean: String) {
    // 앱 이름은 브랜드와 상관없이 "duck duck beep"으로 통일한다(브랜드별 이름 변경 없음)
    HYUNDAI(DuckConfig.APP_NAME, "현대"),
    KIA(DuckConfig.APP_NAME, "기아"),
    GENESIS(DuckConfig.APP_NAME, "제네시스");

    companion object {
        /** INFO_MAKE 값을 소문자로 바꿔 genesis / kia / 그 외(현대)로 나눈다. 읽지 못해도 현대 */
        fun fromMake(make: String?): Brand {
            val m = make.orEmpty().lowercase()
            return when {
                "genesis" in m -> GENESIS
                "kia" in m -> KIA
                else -> HYUNDAI
            }
        }
    }
}

/** 닉네임 = [형용사] [차종]. 예: 듬직한 쏘렌토 */
data class Nickname(val adjective: String, val modelName: String) {
    val text: String get() = "$adjective $modelName"
}

object NicknameBuilder {
    /**
     * 화면에 쓸 차종 이름. 차종 목록에 있는 차(직접 선택·차량 정보·차대번호 대응)는 브랜드를 뺀 이름을 쓰고,
     * 목록에 없으면 브랜드명으로 대신한다 (예: "듬직한 기아").
     */
    fun displayModel(model: MyCarModel, brand: Brand): String = when (model.source) {
        ModelSource.MANUAL, ModelSource.SYSTEM, ModelSource.VIN_TABLE -> model.label.substringAfter(' ', model.label)
        else -> brand.korean
    }

    fun build(adjective: String, model: MyCarModel, brand: Brand) = Nickname(adjective, displayModel(model, brand))
}

/** 조사 '이/가'를 붙인다. 받침 판단: 한글은 글자 구조로, 숫자와 영문은 읽는 소리로 본다 (GV80 -> 영 -> 이) */
object Josa {
    private val digitBatchim = mapOf('0' to true, '1' to true, '2' to false, '3' to true, '4' to false, '5' to false, '6' to true, '7' to true, '8' to true, '9' to false)
    private val letterBatchim = "LMNR" // 엘, 엠, 엔, 알

    fun hasBatchim(word: String): Boolean {
        val c = word.trim().lastOrNull() ?: return false
        return when {
            c in '가'..'힣' -> (c.code - 0xAC00) % 28 != 0
            c.isDigit() -> digitBatchim[c] ?: false
            c.isLetter() -> c.uppercaseChar() in letterBatchim
            else -> false
        }
    }

    fun subject(word: String) = word + if (hasBatchim(word)) "이" else "가"

    /** 조사 '을/를' ("근처의 쏘렌토를", "근처의 투싼을") */
    fun obj(word: String) = word + if (hasBatchim(word)) "을" else "를"
}
