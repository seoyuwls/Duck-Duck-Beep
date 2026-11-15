package com.example.chargesuggest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.chargesuggest.duck.DuckViewModel
import com.example.chargesuggest.ui.duck.DuckApp

/**
 * 같은 차 인사 앱. 차 한 대의 서비스 화면 하나를 보여 주고, 창 너비에 따라 Portrait/Landscape 배치가 바뀐다 (DuckApp).
 * 이전 주제(충전 제안, 외근 비서)의 코드는 참고용으로 남겨 두었고 이 화면에서는 쓰지 않는다.
 */
class MainActivity : ComponentActivity() {

    private val vm: DuckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DuckApp(vm) }
    }
}
