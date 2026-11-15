package com.example.chargesuggest.duck.domain

/*
 * "같은 차종"을 정하는 부분. 우선순위는 위에서 아래로 다음과 같다.
 *
 *  1) 사용자가 설정에서 직접 고른 차종
 *  2) 차량 정보(AAOS CarInfoManager)의 모델명이 우리 차종 목록과 맞는 경우
 *  3) 차대번호(VIN) 앞 8자리가 대응표에 있는 경우
 *  4) 차량 정보의 모델명 그대로 (목록에 없는 차종)
 *  5) 차대번호 앞 8자리 코드 그대로 (같은 코드끼리 같은 차종으로 본다)
 *
 * 차대번호는 차 한 대를 특정하는 값이라 전체를 저장하지도 서버로 보내지도 않는다(앞 8자리만 쓴다).
 * TODO(verify): 실제 Pleos 차량이 모델명을 채워 주는지, 현대·기아·제네시스 차대번호 앞 8자리가 차종을 가르는지 확인해야 한다.
 */

/** 직접 선택할 수 있는 차종 하나. aliases는 차량 정보의 모델명과 맞춰 볼 이름들 */
data class CarModelInfo(val id: String, val label: String, val aliases: List<String> = emptyList())

/** 차량 정보가 알려 주는 제조사·모델명·연식 (차대번호와 달리 차 한 대를 특정하지 않는다) */
data class CarNameInfo(val manufacturer: String?, val model: String?, val modelYear: String?)

/** 직접 선택 목록. TODO(verify): 임시 목록이다. 실제 출시 차종에 맞춰 보완한다 */
object CarModelCatalog {
    val all: List<CarModelInfo> = listOf(
        CarModelInfo("hyundai_ioniq5", "현대 아이오닉 5", listOf("ioniq5", "아이오닉5")),
        CarModelInfo("hyundai_ioniq6", "현대 아이오닉 6", listOf("ioniq6", "아이오닉6")),
        CarModelInfo("hyundai_kona", "현대 코나", listOf("kona", "코나")),
        CarModelInfo("hyundai_casper", "현대 캐스퍼", listOf("casper", "캐스퍼")),
        CarModelInfo("hyundai_avante", "현대 아반떼", listOf("avante", "elantra", "아반떼")),
        CarModelInfo("hyundai_sonata", "현대 쏘나타", listOf("sonata", "쏘나타")),
        CarModelInfo("hyundai_grandeur", "현대 그랜저", listOf("grandeur", "azera", "그랜저")),
        CarModelInfo("hyundai_tucson", "현대 투싼", listOf("tucson", "투싼")),
        CarModelInfo("hyundai_santafe", "현대 싼타페", listOf("santafe", "싼타페")),
        CarModelInfo("hyundai_palisade", "현대 팰리세이드", listOf("palisade", "팰리세이드")),
        CarModelInfo("kia_ev6", "기아 EV6", listOf("ev6")),
        CarModelInfo("kia_ev9", "기아 EV9", listOf("ev9")),
        CarModelInfo("kia_niro", "기아 니로", listOf("niro", "니로")),
        CarModelInfo("kia_sportage", "기아 스포티지", listOf("sportage", "스포티지")),
        CarModelInfo("kia_sorento", "기아 쏘렌토", listOf("sorento", "쏘렌토")),
        CarModelInfo("genesis_g80", "제네시스 G80", listOf("g80")),
        CarModelInfo("genesis_gv70", "제네시스 GV70", listOf("gv70")),
        CarModelInfo("genesis_gv80", "제네시스 GV80", listOf("gv80")),
    )

    fun find(id: String?): CarModelInfo? = all.firstOrNull { it.id == id }

    /** 이름에서 공백·기호를 없애고 소문자로 만든다 ("IONIQ 5" -> "ioniq5") */
    fun normalize(text: String?): String = text.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /** 차량 정보의 모델명을 목록의 차종과 맞춰 본다. 여러 개가 맞으면 가장 긴 이름이 맞은 것을 고른다. 없으면 null */
    fun matchName(modelName: String?): CarModelInfo? {
        val n = normalize(modelName)
        if (n.isEmpty()) return null
        return all
            .mapNotNull { m -> m.aliases.map(::normalize).filter { it.isNotEmpty() && it in n }.maxOfOrNull { it.length }?.let { m to it } }
            .maxByOrNull { it.second }?.first
    }
}

/** 지금 쓰는 차종이 어디서 왔는지 */
enum class ModelSource {
    /** 사용자가 설정에서 직접 선택 */
    MANUAL,
    /** 차량 정보의 모델명이 차종 목록과 맞음 */
    SYSTEM,
    /** 차대번호 앞 8자리가 대응표에 있어서 차종 이름까지 알아냄 */
    VIN_TABLE,
    /** 차량 정보의 모델명은 있지만 차종 목록에는 없음(같은 이름끼리 같은 차종으로 본다) */
    SYSTEM_NAME,
    /** 차대번호는 읽었지만 차종 이름은 모름(같은 코드끼리는 같은 차종으로 본다) */
    VIN_CODE,
    /** 알 수 없음 */
    NONE,
}

/**
 * 내 차종. key가 같은 차끼리 "같은 차종"으로 본다 (서버에 보내는 값은 key뿐이다). key가 null이면 판별할 수 없다.
 */
data class MyCarModel(val key: String?, val label: String, val source: ModelSource)

object VinCode {
    // 표준 차대번호: 17자, 영문 대문자와 숫자(I, O, Q는 쓰지 않는다)
    private val STANDARD_VIN = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    /** 표준 차대번호일 때만 앞 8자리를 돌려준다. 자리표시 값이나 길이가 다른 값은 null */
    fun prefix8(vin: String?): String? {
        val v = vin?.trim()?.uppercase() ?: return null
        return if (STANDARD_VIN.matches(v)) v.take(8) else null
    }
}

/**
 * 차대번호 앞 8자리 -> 차종 id 대응표. 비어 있어도 동작한다(그러면 같은 코드끼리만 같은 차종으로 본다).
 * TODO(verify): 실제 차대번호로 채워야 한다.
 */
class VinModelTable(private val table: Map<String, String> = emptyMap()) {
    fun modelIdOf(prefix8: String): String? = table[prefix8.uppercase()]
}

object CarModelResolver {
    fun resolve(
        manualId: String?,
        vinPrefix8: String?,
        table: VinModelTable = VinModelTable(),
        nameInfo: CarNameInfo? = null,
    ): MyCarModel {
        // 1) 직접 선택
        CarModelCatalog.find(manualId)?.let { return MyCarModel(it.id, it.label, ModelSource.MANUAL) }
        // 2) 차량 정보의 모델명이 목록과 맞음
        CarModelCatalog.matchName(nameInfo?.model)?.let { return MyCarModel(it.id, it.label, ModelSource.SYSTEM) }
        // 3) 차대번호 대응표
        if (vinPrefix8 != null) {
            CarModelCatalog.find(table.modelIdOf(vinPrefix8))?.let { return MyCarModel(it.id, it.label, ModelSource.VIN_TABLE) }
        }
        // 4) 차량 정보의 모델명 그대로
        val model = nameInfo?.model?.trim().orEmpty()
        if (model.isNotEmpty()) {
            val maker = nameInfo?.manufacturer?.trim().orEmpty()
            val key = "name:" + CarModelCatalog.normalize(maker) + "_" + CarModelCatalog.normalize(model)
            return MyCarModel(key, listOf(maker, model).filter { it.isNotEmpty() }.joinToString(" "), ModelSource.SYSTEM_NAME)
        }
        // 5) 차대번호 코드 그대로. 화면에는 차대번호 일부를 보여 주지 않는다(시연 영상 등에서 노출되지 않도록)
        if (vinPrefix8 != null) {
            return MyCarModel("vin:$vinPrefix8", "차대번호로 인식함 (차종 이름은 모름)", ModelSource.VIN_CODE)
        }
        return MyCarModel(null, "차종을 아직 몰라요", ModelSource.NONE)
    }
}
