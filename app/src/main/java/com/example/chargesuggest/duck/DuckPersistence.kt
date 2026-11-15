package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.DuckKind
import com.example.chargesuggest.duck.domain.ReceivedDuck
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 앱을 껐다 켜도 남겨야 하는 것을 저장하고 불러온다.
 *  저장하는 것: 닉네임, 주행 중 알림 설정, 보낸 인형 수, 받은 인형 목록(도감)
 *  저장하지 않는 것: 주행 상태, 근처 상대, 화면에 떠 있는 카드 (매번 새로 정해지는 값)
 *
 * 한 사람(prefix)마다 하나의 문자열(JSON)로 저장한다. 시연에서는 보내는 차("me")와 받는 차("other")를 따로 둔다.
 * 저장된 값이 깨졌거나 인형 종류가 지금 목록에 없으면 그 부분만 건너뛰고 나머지는 살린다.
 */
class DuckPersistence(
    private val store: KeyValueStore,
    prefix: String,
    private val catalog: List<DuckKind> = DuckCatalog.all,
) {
    private val key = "duck_state_$prefix"

    data class Saved(
        val nickname: String,
        val notifyWhileDriving: Boolean,
        val sentCount: Int,
        val items: List<ReceivedDuck>,
    )

    /** 저장된 값이 없거나 읽을 수 없으면 null (처음 켠 것처럼 시작한다) */
    fun load(): Saved? {
        val raw = store.get(key) ?: return null
        return try {
            val o = JSONObject(raw)
            val kinds = catalog.associateBy { it.id }
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i ->
                val it = arr.optJSONObject(i) ?: return@mapNotNull null
                val kind = kinds[it.optString("kind")] ?: return@mapNotNull null   // 없어진 종류는 건너뜀
                val id = it.optString("id").ifEmpty { return@mapNotNull null }
                ReceivedDuck(id, it.optString("from"), kind, it.optLong("at"))
            }
            Saved(
                nickname = o.optString("nickname"),
                notifyWhileDriving = o.optBoolean("notify", true),
                sentCount = o.optInt("sent", 0),
                items = items,
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun save(state: DuckState) {
        val items = JSONArray()
        state.collection.items.forEach {
            items.put(JSONObject().put("id", it.id).put("from", it.fromNickname).put("kind", it.kind.id).put("at", it.receivedAtMs))
        }
        val o = JSONObject()
            .put("nickname", state.nickname)
            .put("notify", state.settings.notifyWhileDriving)
            .put("sent", state.sentCount)
            .put("items", items)
        store.put(key, o.toString())
    }

    fun clear() = store.put(key, null)
}
