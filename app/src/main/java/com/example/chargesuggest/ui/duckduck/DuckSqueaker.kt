package com.example.chargesuggest.ui.duckduck

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.chargesuggest.R
import kotlin.random.Random

/**
 * 오리를 누를 때 나는 "꽥" 효과음. 세 가지(res/raw/duck_quack_*.mp3) 중 하나를 무작위로 낸다.
 * 같은 소리가 연달아 나지 않게 직전에 낸 것은 피하고, 음높이도 조금씩 달리한다.
 * 짧은 효과음을 빠르게 여러 번 겹쳐 낼 수 있는 SoundPool로 재생한다.
 * 소리 종류는 기존 효과음과 같은 "알림 효과(SONIFICATION)"라서 내비 안내 같은 중요한 소리를 방해하지 않는다.
 */
class DuckSqueaker(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = IntArray(SOUNDS.size)
    private val loaded = BooleanArray(SOUNDS.size)
    private var last = -1

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            val i = ids.indexOf(id)
            if (i >= 0 && status == 0) loaded[i] = true
        }
        SOUNDS.forEachIndexed { i, res -> ids[i] = pool.load(context.applicationContext, res, 1) }
    }

    /** 아직 불러오지 못한 소리는 건너뛴다. 불러온 것이 하나도 없으면 조용히 넘어간다 */
    fun play(volume: Float = 0.8f) {
        val ready = SOUNDS.indices.filter { loaded[it] }
        if (ready.isEmpty()) return
        val pick = (ready.filter { it != last }.ifEmpty { ready }).let { it[Random.nextInt(it.size)] }
        last = pick
        pool.play(ids[pick], volume, volume, 1, 0, 0.92f + Random.nextFloat() * 0.2f)
    }

    fun release() = pool.release()

    private companion object {
        val SOUNDS = intArrayOf(R.raw.duck_quack_toy, R.raw.duck_quack_loud, R.raw.duck_quack_squeak)
    }
}
