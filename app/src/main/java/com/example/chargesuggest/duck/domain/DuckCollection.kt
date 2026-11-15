package com.example.chargesuggest.duck.domain

/** 도감. 받은 인형이 최신순으로 쌓인다 */
data class DuckCollection(val items: List<ReceivedDuck> = emptyList()) {

    fun add(duck: ReceivedDuck) = DuckCollection((listOf(duck) + items).take(MAX_ITEMS))

    fun countOf(kindId: String) = items.count { it.kind.id == kindId }

    /** 한 번이라도 받아 본 종류 */
    fun ownedKindIds(): Set<String> = items.map { it.kind.id }.toSet()

    /** 종류별로 몇 개 받았는지 */
    fun countByKind(): Map<String, Int> = items.groupingBy { it.kind.id }.eachCount()

    /** 도감 완성도 0.0~1.0 */
    fun completion(catalog: List<DuckKind> = DuckCatalog.all): Float =
        if (catalog.isEmpty()) 0f else catalog.count { it.id in ownedKindIds() }.toFloat() / catalog.size

    companion object {
        const val MAX_ITEMS = 500
    }
}
