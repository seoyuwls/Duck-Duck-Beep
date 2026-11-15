package com.example.chargesuggest.duck.domain

import kotlin.random.Random

/**
 * 어떤 인형을 보낼지 뽑는다. 주는 사람은 고를 수 없고 랜덤이다.
 * 1) 지금 상황에서 나올 수 있는 인형만 후보로 한다 (조건이 없는 인형은 항상 후보)
 * 2) 후보가 있는 등급들 중에서 확률(weight)로 등급을 뽑는다
 * 3) 그 등급의 후보 중 하나를 고른다
 */
open class DuckRoller(private val catalog: List<DuckKind> = DuckCatalog.all) {

    init {
        require(catalog.any { it.requires == null }) { "조건 없는 인형이 최소 하나는 있어야 합니다" }
    }

    open fun roll(contexts: Set<DuckContext>, rng: Random = Random.Default): DuckKind {
        val eligible = catalog.filter { it.requires == null || it.requires in contexts }
        val byRarity = eligible.groupBy { it.rarity }
        val total = byRarity.keys.sumOf { it.weight }
        var pick = rng.nextInt(total)
        for (r in Rarity.values()) {
            val kinds = byRarity[r] ?: continue
            if (pick < r.weight) return kinds[rng.nextInt(kinds.size)]
            pick -= r.weight
        }
        return eligible.first() // 도달하지 않음
    }
}
