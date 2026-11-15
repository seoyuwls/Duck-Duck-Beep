package com.example.chargesuggest.outing.places

import com.example.chargesuggest.outing.domain.Point
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 카카오 로컬 API "키워드로 장소 검색".
 * 요청: GET https://dapi.kakao.com/v2/local/search/keyword.json?query=...
 * 인증: Authorization: KakaoAK {REST API 키}
 * 응답의 x는 경도, y는 위도(문서 기준).
 */
class KakaoPlaceSearch(
    private val apiKey: String,
    /** 테스트에서 가짜 응답을 끼울 수 있게 분리: (주소, 헤더값) -> 응답 본문 */
    private val http: (url: String, authorization: String) -> String = ::httpGet,
    private val size: Int = 5,
) : PlaceSearch {

    override fun search(query: String, near: Point?): List<Place> {
        if (apiKey.isBlank()) throw PlaceSearchException("카카오 REST API 키가 설정되지 않았어요")
        val sb = StringBuilder("https://dapi.kakao.com/v2/local/search/keyword.json")
        sb.append("?query=").append(URLEncoder.encode(query, "UTF-8"))
        sb.append("&size=").append(size)
        if (near != null) {
            // 가까운 곳을 먼저 보여 주려고 기준 위치를 넘긴다(경도 x, 위도 y)
            sb.append("&x=").append(near.lon).append("&y=").append(near.lat).append("&sort=distance")
        }
        val body = try {
            http(sb.toString(), "KakaoAK $apiKey")
        } catch (e: PlaceSearchException) {
            throw e
        } catch (e: Exception) {
            throw PlaceSearchException("장소 검색에 실패했어요", e)
        }
        return parse(body)
    }

    companion object {
        /** 응답 본문(JSON)을 장소 목록으로 바꾼다. 좌표가 없는 항목은 건너뛴다 */
        fun parse(body: String): List<Place> = try {
            val docs = JSONObject(body).getJSONArray("documents")
            (0 until docs.length()).mapNotNull { i ->
                val d = docs.getJSONObject(i)
                val lon = d.optString("x").toDoubleOrNull()
                val lat = d.optString("y").toDoubleOrNull()
                if (lon == null || lat == null) null
                else Place(
                    name = d.optString("place_name"),
                    // 도로명 주소가 있으면 그것을, 없으면 지번 주소를 쓴다
                    address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                    point = Point(lat, lon),
                )
            }
        } catch (e: JSONException) {
            throw PlaceSearchException("검색 결과를 읽지 못했어요", e)
        }

        private fun httpGet(url: String, authorization: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", authorization)
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                val code = conn.responseCode
                if (code !in 200..299) throw PlaceSearchException("장소 검색 오류 (HTTP $code)")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
