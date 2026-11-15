package com.example.chargesuggest.outing.places

import com.example.chargesuggest.outing.domain.Point

/*
 * 일정에 적힌 장소 글자("강남 고객사", "테헤란로 123")를 좌표로 바꾸는 부분.
 * Pleos 내비 SDK에는 장소 검색이 없어서 외부 지도 API(카카오 로컬)를 쓴다.
 * 외부 API는 데이터(JSON)만 주고, 화면은 우리 앱이 그린다.
 */

data class Place(val name: String, val address: String, val point: Point)

class PlaceSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface PlaceSearch {
    /** 네트워크 호출이므로 메인 스레드에서 부르면 안 된다. 실패하면 [PlaceSearchException] */
    fun search(query: String, near: Point? = null): List<Place>
}

/** 장소 확정 결과: 자동으로 정했거나, 사용자가 고르게 하거나, 못 찾았거나 */
sealed interface Resolution {
    data class Resolved(val place: Place) : Resolution
    data class Choose(val candidates: List<Place>) : Resolution
    data object NotFound : Resolution
}

/**
 * 후보가 하나면 자동으로 정하고, 여러 개면 사용자에게 고르게 한다.
 * 한 번 확정한 장소는 기억해서 같은 글자는 다시 묻지 않는다.
 */
class PlaceResolver(
    private val search: PlaceSearch,
    private val maxCandidates: Int = 5,
) {
    private val confirmed = HashMap<String, Place>()

    fun resolve(text: String, near: Point? = null): Resolution {
        val key = normalize(text)
        if (key.isEmpty()) return Resolution.NotFound
        confirmed[key]?.let { return Resolution.Resolved(it) }

        val found = search.search(text.trim(), near)
        return when {
            found.isEmpty() -> Resolution.NotFound
            found.size == 1 -> Resolution.Resolved(found[0]).also { confirmed[key] = found[0] }
            else -> {
                // 1) 이름이 검색어와 정확히 같은 후보가 하나뿐이면 그것으로 정한다
                // 2) 없으면 이름이 검색어로 시작하는 후보가 하나뿐일 때 정한다 ("강남역" -> "강남역 2호선"만 남는 경우)
                // 둘 다 아니면 사용자가 고르게 한다
                val exact = found.filter { normalize(it.name) == key }
                val prefixed = found.filter { normalize(it.name).startsWith(key) }
                val pick = exact.singleOrNull() ?: prefixed.singleOrNull()
                if (pick != null) Resolution.Resolved(pick).also { confirmed[key] = pick }
                else Resolution.Choose(found.take(maxCandidates))
            }
        }
    }

    /** 사용자가 후보 중 하나를 골랐을 때 */
    fun confirm(text: String, place: Place) {
        confirmed[normalize(text)] = place
    }

    private fun normalize(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
