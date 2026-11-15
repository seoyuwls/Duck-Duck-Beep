package com.example.chargesuggest.duck

import com.example.chargesuggest.ui.duck.frameIndexFor
import org.junit.Assert.assertEquals
import org.junit.Test

class PonyTurntableTest {
    private val n = 36 // 10도마다 한 장

    @Test fun `처음에는 정면 이미지다`() = assertEquals(0, frameIndexFor(0f, n))

    @Test
    fun `오른쪽으로 끌면 번호가 줄어들고 왼쪽으로 끌면 늘어난다`() {
        assertEquals(35, frameIndexFor(10f, n))   // 각도가 늘면(오른쪽 드래그) 한 칸 뒤로
        assertEquals(1, frameIndexFor(-10f, n))   // 각도가 줄면(왼쪽 드래그) 한 칸 앞으로
    }

    @Test
    fun `한 바퀴를 넘겨도 처음으로 돌아온다`() {
        assertEquals(0, frameIndexFor(360f, n))
        assertEquals(0, frameIndexFor(-720f, n))
        assertEquals(1, frameIndexFor(-370f, n))
    }

    @Test
    fun `반 바퀴는 반대편 이미지다`() = assertEquals(18, frameIndexFor(180f, n))

    @Test
    fun `이미지가 없거나 한 장이어도 오류 없이 0을 돌려준다`() {
        assertEquals(0, frameIndexFor(123f, 0))
        assertEquals(0, frameIndexFor(123f, 1))
    }

    @Test
    fun `이미지 개수가 달라도 범위를 벗어나지 않는다`() {
        for (count in listOf(2, 8, 24, 72)) {
            var a = -1000f
            while (a < 1000f) {
                val i = frameIndexFor(a, count)
                assert(i in 0 until count) { "범위 밖: angle=$a count=$count index=$i" }
                a += 7.3f
            }
        }
    }
}
