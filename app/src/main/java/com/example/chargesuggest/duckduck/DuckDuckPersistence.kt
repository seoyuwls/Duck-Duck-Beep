package com.example.chargesuggest.duckduck

import com.example.chargesuggest.duck.KeyValueStore
import com.example.chargesuggest.duckduck.domain.BookEntry
import com.example.chargesuggest.duckduck.domain.DuckBook
import com.example.chargesuggest.duckduck.domain.DuckConfig
import com.example.chargesuggest.duckduck.domain.DuckType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 앱을 껐다 켜도 남겨야 하는 것: 온보딩 완료 여부, 고른 형용사, 설정, 도감(받은 오리와 확인 여부).
 * 저장하지 않는 것: 티켓·근처 차·화면 카드 (15분짜리이거나 매번 새로 정해지는 값), 위치.
 * 저장된 값이 깨졌으면 null(처음 켠 것처럼 시작), 지금 목록에 없는 오리 종류는 그 항목만 건너뛴다.
 */
class DuckDuckPersistence(private val store: KeyValueStore, private val key: String = "duckduck_state") {

    data class Saved(
        val onboarded: Boolean,
        val adjective: String?,
        val settings: DuckDuckSettings,
        val book: DuckBook,
        /** 지금까지 보낸 오리 수(누적) */
        val sentCount: Int = 0,
        /** 날씨 오리 선물 기회를 마지막으로 쓴 날(한국 시간 기준 일 번호). 0이면 아직 없음 */
        val giftDay: Long = 0L,
        /** 튜토리얼 진행 단계: 1~3이면 진행 중, 0이면 끝남(또는 건너뜀) */
        val tutorialStep: Int = 0,
    )

    fun load(): Saved? {
        val raw = store.get(key) ?: return null
        return try {
            val o = JSONObject(raw)
            val types = DuckType.values().associateBy { it.id }
            val arr = o.optJSONArray("entries") ?: JSONArray()
            val entries = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = types[e.optString("type")] ?: return@mapNotNull null
                BookEntry(e.optInt("id"), type, e.optString("from"), e.optLong("at"), e.optBoolean("opened"))
            }
            // 다음 번호가 겹치지 않게, 저장된 값과 가장 큰 번호+1 중 큰 쪽을 쓴다
            val nextId = maxOf(o.optInt("nextId", 1), (entries.maxOfOrNull { it.id } ?: 0) + 1)
            Saved(
                onboarded = o.optBoolean("onboarded", false),
                adjective = o.optString("adjective").ifEmpty { null },
                settings = DuckDuckSettings(
                    soundOn = o.optBoolean("soundOn", DuckConfig.PLAY_REACTION_SOUND),
                    ambientOn = o.optBoolean("ambientOn", true),
                    ambientRgb = o.optInt("ambientRgb", DuckConfig.AMBIENT_RGB) and 0xFFFFFF,
                ),
                book = DuckBook(entries, nextId),
                sentCount = o.optInt("sent", 0),
                giftDay = o.optLong("giftDay", 0L),
                tutorialStep = o.optInt("tutorial", 0),
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun save(onboarded: Boolean, adjective: String?, settings: DuckDuckSettings, book: DuckBook, sentCount: Int = 0, giftDay: Long = 0L, tutorialStep: Int = 0) {
        val entries = JSONArray()
        book.entries.forEach {
            entries.put(
                JSONObject().put("id", it.id).put("type", it.type.id).put("from", it.fromNickname)
                    .put("at", it.receivedAtMs).put("opened", it.opened),
            )
        }
        val o = JSONObject()
            .put("onboarded", onboarded)
            .put("adjective", adjective ?: "")
            .put("soundOn", settings.soundOn)
            .put("ambientOn", settings.ambientOn)
            .put("ambientRgb", settings.ambientRgb)
            .put("nextId", book.nextId)
            .put("sent", sentCount)
            .put("giftDay", giftDay)
            .put("tutorial", tutorialStep)
            .put("entries", entries)
        store.put(key, o.toString())
    }

    fun clear() = store.put(key, null)
}
