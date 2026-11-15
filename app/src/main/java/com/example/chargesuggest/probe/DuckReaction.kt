package com.example.chargesuggest.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.PI
import kotlin.math.sin

/**
 * 인형을 받았을 때의 "화면 밖 반응"이 앱이 보이지 않을 때도 되는지 시험하는 실험 코드.
 * 조명(Vehicle Light) / 효과음(AudioTrack) / 시스템 알림(NotificationManager) 세 가지를 각각 시험한다.
 * 결과는 Logcat 태그 PleosProbe 로 남긴다.
 */
class DuckReaction(private val context: Context) {

    private val tag = "PleosProbe"
    private val area = SeatArea(SeatValue.ROW_1_LEFT)
    private var vehicle: Vehicle? = null

    private fun log(msg: String) = Log.d(tag, "[오리] $msg")

    private fun vehicle(): Vehicle {
        vehicle?.let { return it }
        return Vehicle(context.applicationContext).also { it.initialize(); vehicle = it }
    }

    /** 앰비언트 조명: 지원 여부 -> 현재 값 -> 값 변경 -> 다시 읽기 */
    fun ambient(color: Int) {
        val light = vehicle().getLight()
        light.checkAmbientLightBrightnessColorCapabilities(
            onComplete = { cfg, areas ->
                log("조명 색 지원: config=$cfg 영역=${areas.map { a -> a.values.map { it.toString() } }}")
            },
            onFailed = { e -> log("조명 색 지원 확인 실패: ${e.javaClass.simpleName} ${e.message}") },
        )
        // 지원 영역으로 알려진 값을 하나씩 시도한다 (어느 영역이 실제로 되는지 확인)
        // 개별 좌석은 모두 거부되어서, 영역 없음(전역)과 전체 묶음도 시도한다
        val candidates = listOf("GLOBAL", "ALL5", "ROW_1_LEFT")
        fun areaOf(name: String) = when (name) {
            "GLOBAL" -> SeatArea()
            "ALL5" -> SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
            else -> SeatArea(SeatValue.ROW_1_LEFT)
        }
        for (sv in candidates) {
            val a = areaOf(sv)
            light.getAmbientLightBrightnessColor(
                area = a,
                onComplete = { v -> log("[$sv] 조명 색 현재값=$v") },
                onFailed = { e -> log("[$sv] 조명 색 읽기 실패: ${e.message}") },
            )
            light.setAmbientLightBrightnessColor(
                area = a, value = color,
                onComplete = { ar, v -> log("[$sv] 조명 색 설정 성공: 영역=${ar.values.toList()} 값=$v") },
                onFailed = { e -> log("[$sv] 조명 색 설정 실패: ${e.message}") },
            )
            light.setAmbientLightSwitch(
                area = a, value = ai.pleos.playground.vehicle.constant.control.LightSwitch.ON,
                onComplete = { ar, v -> log("[$sv] 조명 스위치 설정 성공: 값=$v") },
                onFailed = { e -> log("[$sv] 조명 스위치 설정 실패: ${e.message}") },
            )
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            for (sv in candidates) light.getAmbientLightBrightnessColor(
                area = areaOf(sv),
                onComplete = { v -> log("[$sv] 조명 색 변경 후 값=$v") },
                onFailed = { e -> log("[$sv] 조명 색 재확인 실패: ${e.message}") },
            )
        }, 1500)
    }

    /** 같은 차종 인식 시험: 차대번호(VIN)가 읽히는지. 개인 식별 위험이 있어서 전체를 남기지 않고 길이와 앞 8자리만 로그에 남긴다 */
    fun vin() {
        val info = vehicle().getCarInfo()
        info.checkVINInfoCapabilities(
            { cfg -> log("VIN 지원: $cfg") },
            { e -> log("VIN 지원 확인 실패: ${e.javaClass.simpleName} ${e.message}") },
        )
        info.getVINInfo(
            { v -> log("VIN 읽음: 길이=${v?.length} 앞 8자리=${v?.take(8)}") },
            { e -> log("VIN 읽기 실패: ${e.javaClass.simpleName} ${e.message}") },
        )
    }

    /** 짧은 효과음(0.4초): 파일 없이 사인파를 만들어 재생한다. 알림용 오디오 속성을 쓴다 */
    fun sound() {
        val rate = 22050
        val n = (rate * 0.4).toInt()
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = 1.0 - i.toDouble() / n
            (sin(2 * PI * (520 + 700 * t) * t) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder().setSampleRate(rate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val track = AudioTrack(attrs, fmt, n * 2, AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
        track.write(pcm, 0, n)
        track.setVolume(0.3f) // 내비 안내보다 작게 시험
        track.play()
        log("효과음 재생 시작: state=${track.playState} (3=PLAYING)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            log("효과음 종료 후 state=${track.playState}"); track.release()
        }, 700)
    }

    /** 시스템 알림: 일반 카테고리(헤드업 대상 아님)와 메시지 카테고리를 각각 올려 본다 */
    fun notifyPlain() = post(10, null, "오리를 받았어요 (일반)", "닉네임: 꽥꽥이")
    fun notifyMessage() = post(11, androidx.core.app.NotificationCompat.CATEGORY_MESSAGE, "오리를 받았어요 (메시지 카테고리)", "닉네임: 꽥꽥이")

    private fun post(id: Int, category: String?, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ch = "duck_probe"
        nm.createNotificationChannel(NotificationChannel(ch, "오리 시험", NotificationManager.IMPORTANCE_HIGH))
        val b = NotificationCompat.Builder(context, ch)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (category != null) b.setCategory(category)
        val enabled = nm.areNotificationsEnabled()
        nm.notify(id, b.build())
        log("알림 게시: id=$id category=$category 알림허용=$enabled")
    }
}
