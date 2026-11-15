package com.example.chargesuggest.ui.duck

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 포니 회전 보기: 3D 모델을 여러 방향에서 미리 찍어 둔 이미지를 손가락 움직임에 맞춰 넘겨서 돌아가는 것처럼 보여 준다.
 * 3D 엔진을 쓰지 않아서 시스템 화면과 충돌하지 않고 CPU와 메모리를 적게 쓴다.
 * (실시간 3D 엔진은 에뮬레이터에서 시스템 주행 화면이 크래시해서 쓰지 않기로 했다.)
 *
 * 이미지 만드는 법: tools/turntable/README.md
 * 이미지 위치: assets/pony/turntable/<인형 id>_00.png ~ <인형 id>_35.png (36방향, 0번이 정면)
 *
 * 규칙
 *  - 도감 상세(정차·주차)에서만 보여 준다. 주행 중에는 도감 자체가 잠긴다.
 *  - 손가락을 움직일 때만 이미지가 바뀐다. 스스로 움직이는 애니메이션은 없다.
 */

const val TURNTABLE_DIR = "pony/turntable"

/** 회전 이미지 파일 경로 목록(이름순). 인형 id로 시작하는 파일이 없으면 기본 포니(pony_basic)를 쓴다. 없으면 빈 목록 */
fun turntableFrames(context: Context, kindId: String): List<String> {
    val all = runCatching { context.assets.list(TURNTABLE_DIR)?.toList() }.getOrNull().orEmpty()
    for (id in listOf(kindId, "pony_basic")) {
        val frames = all.filter { it.startsWith("${id}_") }.sorted()
        if (frames.isNotEmpty()) return frames.map { "$TURNTABLE_DIR/$it" }
    }
    return emptyList()
}

/** 드래그한 거리(px)를 회전 각도로 바꾸는 비율. 400px 끌면 약 160도 */
private const val DEGREES_PER_PX = 0.4f

/** 각도(도)와 이미지 개수로 지금 보여 줄 이미지 번호를 구한다. 오른쪽으로 끌면 앞면이 오른쪽으로 넘어가도록 번호가 줄어든다 */
internal fun frameIndexFor(angleDeg: Float, frameCount: Int): Int {
    if (frameCount <= 0) return 0
    val turns = ((-angleDeg % 360f) + 360f) % 360f / 360f
    return (turns * frameCount).toInt().coerceIn(0, frameCount - 1)
}

@Composable
fun PonyTurntable(
    framePaths: List<String>,
    modifier: Modifier = Modifier,
    description: String = "포니 회전 보기",
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    // 모든 이미지를 한 번에 읽어 둔다(36장 x 512x512 이면 약 37MB). 읽는 동안은 빈 화면이다
    val loaded by produceState<Result<List<ImageBitmap>>?>(initialValue = null, framePaths) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                framePaths.map { path ->
                    context.assets.open(path).use { BitmapFactory.decodeStream(it)!!.asImageBitmap() }
                }
            }
        }
    }
    val frames = loaded?.getOrNull()
    LaunchedEffect(loaded) { if (loaded?.isFailure == true) onFailed() }

    var angle by remember { mutableFloatStateOf(0f) }
    if (frames.isNullOrEmpty()) return
    Image(
        bitmap = frames[frameIndexFor(angle, frames.size)],
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize().pointerInput(frames.size) {
            detectHorizontalDragGestures { _, dragAmount -> angle += dragAmount * DEGREES_PER_PX }
        },
    )
}
