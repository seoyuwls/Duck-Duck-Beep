package com.example.chargesuggest.duck

import android.content.Context

/** 앱을 껐다 켜도 남는 작은 설정값 저장소 (SharedPreferences) */
class SharedPrefsStore(context: Context, name: String = "pony_prefs") : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
