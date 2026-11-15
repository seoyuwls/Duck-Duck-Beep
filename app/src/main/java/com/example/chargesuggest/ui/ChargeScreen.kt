package com.example.chargesuggest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargesuggest.RouteStatus
import com.example.chargesuggest.UiState
import com.example.chargesuggest.domain.Advice
import com.example.chargesuggest.domain.DriveState

/**
 * 같은 데이터를 상태별로 다르게 그린다.
 *   주행(D)       : 글자와 정보를 줄이고, 큰 버튼 2개만 둔다 (음성 안내가 주역)
 *   주차(P)       : 설명과 세부 정보를 함께 보여준다
 *   창이 넓을 때  : 오른쪽에 부가 정보를 덧붙인다 (Landscape 2칸)
 */
@Composable
fun ChargeScreen(state: UiState, onAccept: () -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        // Pleos Connect v2.1.0 에뮬레이터 실측: Portrait(앱 2개) 841dp, Landscape(앱 1개) 1682dp.
        // 둘 사이인 1200dp를 기준으로 삼는다. 방향이 아니라 창 너비로 판단한다.
        val isWide = maxWidth > 1200.dp
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (state.driveState) {
                    DriveState.DRIVING -> DrivingContent(state, onAccept, onDismiss)
                    DriveState.PARKED -> ParkedContent(state, onAccept, onDismiss)
                }
            }
            if (isWide) {
                Column(Modifier.weight(1f)) {
                    Text("배터리 ${state.soc}%", fontSize = 24.sp)
                    // TODO: 주행 가능 거리, 근처 충전소 목록 등 확장 정보
                }
            }
        }
    }
}

@Composable
private fun DrivingContent(state: UiState, onAccept: () -> Unit, onDismiss: () -> Unit) {
    // 주행 중: 짧은 문장, 큰 글자, 한 번 탭으로 끝
    when (state.advice) {
        is Advice.SuggestCharge -> {
            Text("배터리 ${state.soc}%", fontSize = 36.sp)
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("충전소 추가", fontSize = 28.sp)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("나중에", fontSize = 28.sp)
            }
        }
        Advice.None -> Text(statusLine(state), fontSize = 36.sp)
    }
}

@Composable
private fun ParkedContent(state: UiState, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Text("배터리 ${state.soc}%", fontSize = 28.sp)
    when (state.advice) {
        is Advice.SuggestCharge -> {
            Text("배터리가 부족해요. 가까운 충전소를 경로에 넣을까요?", fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAccept) { Text("충전소 추가", fontSize = 20.sp) }
                OutlinedButton(onClick = onDismiss) { Text("나중에", fontSize = 20.sp) }
            }
        }
        Advice.None -> Text(statusLine(state), fontSize = 20.sp)
    }
}

private fun statusLine(state: UiState): String = when (state.routeStatus) {
    RouteStatus.ADDED -> "충전소를 경로에 넣었어요"
    RouteStatus.FAILED -> "충전소를 넣지 못했어요. 다시 시도해 주세요"
    RouteStatus.IDLE -> "배터리가 충분해요"
}
