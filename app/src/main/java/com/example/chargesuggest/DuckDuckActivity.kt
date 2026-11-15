package com.example.chargesuggest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.chargesuggest.duckduck.DuckDuckViewModel
import com.example.chargesuggest.ui.duckduck.DuckDuckApp

/**
 * Duck Duck Beep! 앱의 시작 화면 (duck-app-spec.md 기준).
 * 이전 포니 시연 화면은 MainActivity에 남아 있다: adb shell am start -n com.example.chargesuggest/.MainActivity
 */
class DuckDuckActivity : ComponentActivity() {

    private val vm: DuckDuckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 확인용: --ei width_dp 841 로 창 너비를 고정해 Portrait 배치를 본다
        val forced = intent.getIntExtra("width_dp", 0).takeIf { it > 0 }
        setContent { DuckDuckApp(vm, forced) }
    }
}
