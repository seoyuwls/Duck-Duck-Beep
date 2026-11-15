package com.example.chargesuggest.ui.duckduck

import android.content.Context

/*
 * 오리 회전 보기: 3D 모델을 36방향에서 미리 찍어 둔 이미지를 손가락에 맞춰 넘긴다 (ui/duck/PonyTurntable.kt의 같은 방식).
 * 3D 엔진을 쓰지 않아서 시스템 화면과 충돌하지 않고 CPU와 메모리를 적게 쓴다.
 *
 * 이미지 위치: app/src/main/assets/duck/turntable/duck_<오리 id>_00.png ~ duck_<오리 id>_35.png
 *   오리 id: rainbow, snowman, raincoat, umbrella, sunglasses, scarf, basic   (예: duck_basic_00.png)
 *   0번이 정면. 이미지를 만드는 도구는 tools/turntable/README.md 참고(GLB 3D 모델 -> 36방향 이미지).
 * 이 폴더에 이미지가 없으면 정지 이미지(res/drawable/duck_basic.webp)를 보여 준다.
 */
const val DUCK_TURNTABLE_DIR = "duck/turntable"

/** 오리 id의 회전 이미지 경로 목록(이름순). 없으면 빈 목록 */
fun duckTurntableFrames(context: Context, typeId: String): List<String> {
    val all = runCatching { context.assets.list(DUCK_TURNTABLE_DIR)?.toList() }.getOrNull().orEmpty()
    val prefix = "duck_${typeId}_"
    return all.filter { it.startsWith(prefix) }.sorted().map { "$DUCK_TURNTABLE_DIR/$it" }
}
