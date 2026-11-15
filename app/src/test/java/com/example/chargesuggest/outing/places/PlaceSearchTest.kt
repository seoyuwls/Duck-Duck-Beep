package com.example.chargesuggest.outing.places

import com.example.chargesuggest.outing.domain.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSearch(val results: Map<String, List<Place>>) : PlaceSearch {
    var calls = 0
    override fun search(query: String, near: Point?): List<Place> {
        calls++
        return results[query] ?: emptyList()
    }
}

class PlaceSearchTest {

    private fun place(name: String, lat: Double = 37.5, lon: Double = 127.0) = Place(name, "주소", Point(lat, lon))

    // 카카오 문서의 응답 형식을 따른 샘플 (x=경도, y=위도)
    private val sample = """
        {"meta":{"total_count":3,"is_end":true},
         "documents":[
          {"place_name":"강남 고객사","address_name":"서울 강남구 역삼동 1","road_address_name":"서울 강남구 테헤란로 1","x":"127.0276","y":"37.4979"},
          {"place_name":"강남 지점","address_name":"서울 강남구 논현동 2","road_address_name":"","x":"127.0300","y":"37.5000"},
          {"place_name":"좌표 없음","address_name":"어딘가","road_address_name":"","x":"","y":""}
         ]}
    """.trimIndent()

    @Test
    fun `카카오 응답을 장소 목록으로 바꾼다`() {
        val places = KakaoPlaceSearch.parse(sample)
        assertEquals(2, places.size)  // 좌표가 없는 항목은 제외
        assertEquals("강남 고객사", places[0].name)
        assertEquals("서울 강남구 테헤란로 1", places[0].address)      // 도로명 주소 우선
        assertEquals("서울 강남구 논현동 2", places[1].address)        // 없으면 지번 주소
        assertEquals(37.4979, places[0].point.lat, 1e-9)               // y = 위도
        assertEquals(127.0276, places[0].point.lon, 1e-9)              // x = 경도
    }

    @Test
    fun `깨진 응답은 검색 오류로 알린다`() {
        val r = runCatching { KakaoPlaceSearch.parse("not json") }
        assertTrue(r.exceptionOrNull() is PlaceSearchException)
    }

    @Test
    fun `요청에는 인증 헤더와 인코딩된 검색어가 들어간다`() {
        var url = ""; var auth = ""
        val search = KakaoPlaceSearch("KEY", http = { u, a -> url = u; auth = a; sample })
        search.search("강남 고객사", Point(37.5, 127.0))
        assertEquals("KakaoAK KEY", auth)
        assertTrue(url.startsWith("https://dapi.kakao.com/v2/local/search/keyword.json?query="))
        assertTrue(url.contains("x=127.0") && url.contains("y=37.5"))
        assertTrue(!url.contains("강남"))  // 한글은 인코딩되어야 한다
    }

    @Test
    fun `키가 비어 있으면 호출하지 않고 오류를 알린다`() {
        val r = runCatching { KakaoPlaceSearch("", http = { _, _ -> error("호출되면 안 됨") }).search("강남") }
        assertTrue(r.exceptionOrNull() is PlaceSearchException)
    }

    @Test
    fun `후보가 하나면 자동으로 정한다`() {
        val resolver = PlaceResolver(FakeSearch(mapOf("강남 고객사" to listOf(place("강남 고객사")))))
        assertTrue(resolver.resolve("강남 고객사") is Resolution.Resolved)
    }

    @Test
    fun `후보가 여러 개면 사용자에게 고르게 한다`() {
        val resolver = PlaceResolver(FakeSearch(mapOf("강남" to listOf(place("강남역"), place("강남 지점")))))
        val r = resolver.resolve("강남")
        assertEquals(2, (r as Resolution.Choose).candidates.size)
    }

    @Test
    fun `이름이 정확히 같은 후보가 하나뿐이면 자동으로 정한다`() {
        val resolver = PlaceResolver(FakeSearch(mapOf("강남역" to listOf(place("강남역 2번 출구"), place("강남역")))))
        val r = resolver.resolve("강남역") as Resolution.Resolved
        assertEquals("강남역", r.place.name)
    }

    @Test
    fun `이름이 검색어로 시작하는 후보가 하나뿐이면 자동으로 정한다`() {
        val resolver = PlaceResolver(
            FakeSearch(mapOf("강남역" to listOf(place("강남역 2호선"), place("놀숲 강남역점"), place("카페 강남역 앞")))),
        )
        val r = resolver.resolve("강남역") as Resolution.Resolved
        assertEquals("강남역 2호선", r.place.name)
    }

    @Test
    fun `검색어로 시작하는 후보가 여러 개면 고르게 한다`() {
        val resolver = PlaceResolver(FakeSearch(mapOf("판교역" to listOf(place("판교역 신분당선"), place("판교역 경강선")))))
        assertTrue(resolver.resolve("판교역") is Resolution.Choose)
    }

    @Test
    fun `결과가 없으면 못 찾았다고 알린다`() {
        assertTrue(PlaceResolver(FakeSearch(emptyMap())).resolve("없는 곳") is Resolution.NotFound)
        assertTrue(PlaceResolver(FakeSearch(emptyMap())).resolve("   ") is Resolution.NotFound)
    }

    @Test
    fun `한 번 확정한 장소는 다시 검색하지 않는다`() {
        val fake = FakeSearch(mapOf("강남" to listOf(place("강남역"), place("강남 지점"))))
        val resolver = PlaceResolver(fake)
        resolver.resolve("강남")
        resolver.confirm("강남", place("강남 지점"))
        val again = resolver.resolve(" 강남 ") as Resolution.Resolved
        assertEquals("강남 지점", again.place.name)
        assertEquals(1, fake.calls)
    }
}
