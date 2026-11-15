package com.example.chargesuggest.ui.duck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import com.example.chargesuggest.duck.CarModelHolder
import com.example.chargesuggest.duck.domain.CarModelCatalog
import com.example.chargesuggest.duck.domain.ModelSource
import com.example.chargesuggest.duck.domain.MyCarModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chargesuggest.duck.DuckController
import com.example.chargesuggest.duck.DuckState
import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DuckCatalog
import com.example.chargesuggest.duck.domain.GiveCheck
import com.example.chargesuggest.duck.domain.NicknameCheck
import com.example.chargesuggest.duck.domain.ReactionPlanner
import com.example.chargesuggest.duck.domain.ReceivedDuck
import kotlinx.coroutines.delay

/**
 * 서비스 화면 한 개(차 한 대). 창 너비에 따라 배치가 바뀐다.
 *
 *  - 좁을 때(Portrait, 약 841dp): 핵심 기능만. 메인 / 도감 / 설정을 탭으로 전환
 *  - 넓을 때(Landscape, 약 1682dp): 왼쪽에 메인, 오른쪽에 도감·설정(확장 정보)을 함께 보여 준다
 *
 * 화면 내용
 *  - 메인: 같은 차를 발견했을 때 "전달하기"
 *  - 도감: 받은 포니 (주행 중에는 열 수 없음)
 *  - 설정: 닉네임, 주행 중 알림 끄기 (주행 중에는 입력 불가)
 *  - 수령 카드: 받자마자 2.5초 동안 떴다가 자동으로 사라짐. 확인만 하고 조작은 없음
 */
@Composable
fun CarView(
    controller: DuckController,
    state: DuckState,
    reactor: DemoReactor,
    carModel: CarModelHolder,
    wide: Boolean,
    demoTag: String,
    modifier: Modifier = Modifier,
) {
    val c = duckColors
    val model by carModel.model.collectAsState()
    var tab by remember { mutableIntStateOf(0) }      // 좁을 때: 0 메인, 1 도감, 2 설정
    var sideTab by remember { mutableIntStateOf(0) }  // 넓을 때 오른쪽: 0 도감, 1 설정
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.nearby) { message = null }
    // 카드는 정해진 시간 뒤 자동으로 사라진다 (주행 중 시선을 오래 잡지 않도록)
    LaunchedEffect(state.card) {
        if (state.card != null) { delay(ReactionPlanner.CARD_MS); controller.dismissCard() }
    }

    val onGive: () -> Unit = {
        message = when {
            state.nickname.isBlank() -> "설정에서 닉네임을 먼저 정해 주세요"
            else -> when (controller.give()) {
                GiveCheck.ALLOWED -> "전달했어요"
                GiveCheck.NO_TARGET -> "근처에 같은 차가 없어요"
                GiveCheck.DAILY_LIMIT -> "오늘은 더 보낼 수 없어요"
                GiveCheck.TARGET_COOLDOWN -> "이 분께는 이미 보냈어요"
            }
        }
    }

    Box(modifier.clip(RoundedCornerShape(32.dp)).background(c.basic00)) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("포니 인사", style = DuckType.titleMedium, color = c.textPrimary)
                    Text(demoTag, style = DuckType.labelMedium, color = c.textTertiary) // 시연 안내용 표시
                }
                StatusChip(state.driveMode)
            }
            AmbientIndicator(reactor)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { MainPanel(state, message, onGive, modelKnown = model.key != null) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SegmentedTabs(listOf("도감", "설정"), sideTab, { sideTab = it })
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (sideTab == 0) CollectionPanel(state) else SettingsPanel(state, controller, model, carModel::chooseManual)
                        }
                    }
                }
            } else {
                SegmentedTabs(listOf("메인", "도감", "설정"), tab, { tab = it })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> MainPanel(state, message, onGive, modelKnown = model.key != null)
                        1 -> CollectionPanel(state)
                        else -> SettingsPanel(state, controller, model, carModel::chooseManual)
                    }
                }
            }
        }
        // 조명 표시줄과 탭을 가리지 않도록 그 아래에 띄운다
        state.card?.let {
            ReceiveCard(
                it,
                Modifier.align(Alignment.TopCenter).fillMaxWidth(if (wide) 0.55f else 1f)
                    .padding(top = if (wide) 200.dp else 290.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

// ---------------- 메인 ----------------

@Composable
fun MainPanel(state: DuckState, message: String?, onGive: () -> Unit, modelKnown: Boolean = true) {
    val c = duckColors
    val driving = state.driveMode == DriveMode.DRIVING
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.nearby != null) {
                Text("같은 차를 발견했어요", style = DuckType.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("전달하면 랜덤 인형이 가요", style = DuckType.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                // 주행 중에는 한눈에 보이고 누르기 쉽도록 더 크게
                BasicButton("전달하기", onGive, Modifier.fillMaxWidth(), height = if (driving) 150.dp else 110.dp)
            } else {
                Text("근처에 같은 차가 없어요", style = DuckType.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("같은 차를 만나면 알려 드려요", style = DuckType.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
            }
            message?.let {
                Spacer(Modifier.height(28.dp))
                Text(it, style = DuckType.bodyMedium, color = c.textPrimary, textAlign = TextAlign.Center)
            }
        }
        if (!driving) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "보낸 인형 ${state.sentCount}개 · 받은 인형 ${state.collection.items.size}개",
                    style = DuckType.bodySmall, color = c.textSecondary,
                )
                // 차종을 모르면 같은 차를 찾을 수 없으니 설정에서 정하도록 알려 준다
                if (!modelKnown) {
                    Text("설정에서 내 차종을 정하면 같은 차를 찾을 수 있어요", style = DuckType.labelMedium, color = c.textSecondary)
                }
            }
        }
    }
}

// ---------------- 수령 카드 ----------------

/** 받자마자 뜨는 카드. 움직임 없이 정지 화면이고 한 줄씩만 보여 준다 */
@Composable
fun ReceiveCard(duck: ReceivedDuck, modifier: Modifier = Modifier) {
    val c = duckColors
    val border = rarityColor(duck.kind.rarity)
    Row(
        modifier.fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(32.dp)) // 그림자 Level 3 (문서 3.5)
            .clip(RoundedCornerShape(32.dp)).background(c.basic00)
            .border(4.dp, border, RoundedCornerShape(32.dp))
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        DuckImage(duck.kind, 140.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("포니를 받았어요", style = DuckType.titleSmall, color = c.textPrimary)
            Text("${duck.fromNickname}님이 보냈어요", style = DuckType.bodyMedium, color = c.textSecondary, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityTag(duck.kind.rarity)
                Text(duck.kind.name, style = DuckType.bodySmall, color = c.textPrimary, maxLines = 1)
            }
        }
    }
}

// ---------------- 도감 ----------------

@Composable
fun CollectionPanel(state: DuckState) {
    val c = duckColors
    var selected by remember { mutableStateOf<ReceivedDuck?>(null) }  // 눌러서 3D로 보는 포니
    if (state.driveMode == DriveMode.DRIVING) {
        // 도감은 주행 중에는 열 수 없다 (정차·주차에서만)
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("주행 중에는 볼 수 없어요", style = DuckType.titleSmall, color = c.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("정차하면 도감이 열려요", style = DuckType.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        return
    }
    // 3D 상세. 주행 중에는 위에서 이미 막혀 있어서 3D 렌더링이 돌지 않는다
    selected?.let { PonyDetail(it, onClose = { selected = null }); return }
    val owned = state.collection.ownedKindIds()
    val multiKind = DuckCatalog.active.size > 1
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 종류가 하나뿐이면 "1/1" 대신 받은 개수만 크게 보여 준다
                Text(
                    if (multiKind) "도감 ${owned.size}/${DuckCatalog.active.size}" else "받은 포니 ${state.collection.items.size}개",
                    style = DuckType.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f),
                )
                if (multiKind) Text("받은 인형 ${state.collection.items.size}개", style = DuckType.bodySmall, color = c.textSecondary)
            }
        }
        // 종류가 여럿일 때만: 수집 현황을 한 줄로 (받아 본 인형은 색으로, 아직이면 실루엣)
        if (multiKind) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DuckCatalog.active.forEach { kind ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) { DuckImage(kind, 64.dp, silhouette = kind.id !in owned) }
                }
            }
        }
        // 받은 기록(닉네임 + 인형 이미지)이 도감의 중심이라 바로 보이게 한다
        item {
            Column(Modifier.padding(top = 4.dp)) {
                Text("받은 기록", style = DuckType.bodyLarge, color = c.textPrimary)
                if (state.collection.items.isNotEmpty()) {
                    Text("눌러서 3D로 돌려 볼 수 있어요", style = DuckType.labelMedium, color = c.textSecondary)
                }
            }
        }
        if (state.collection.items.isEmpty()) {
            item { Text("아직 받은 인형이 없어요", style = DuckType.bodySmall, color = c.textSecondary) }
        }
        items(state.collection.items, key = { it.id }) { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh)
                    .clickable { selected = d }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DuckImage(d.kind, 100.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.fromNickname, style = DuckType.bodyLarge, color = c.textPrimary, maxLines = 1)
                    Text(d.kind.name, style = DuckType.bodySmall, color = c.textSecondary, maxLines = 1)
                }
                RarityTag(d.kind.rarity)
            }
        }
    }
}

/**
 * 도감 상세: 받은 포니를 손가락으로 돌려 본다(회전 이미지). 정차·주차에서만 열린다.
 * assets/pony/turntable/<인형 id>_00.png ~ 가 있으면 그것을, 없으면 pony_basic 이미지를 쓴다.
 * 이미지가 없거나 읽지 못하면 2D 그림으로 대신한다.
 */
@Composable
fun PonyDetail(duck: ReceivedDuck, onClose: () -> Unit) {
    val c = duckColors
    val context = LocalContext.current
    val frames = remember(duck.kind.id) { turntableFrames(context, duck.kind.id) }
    var failed by remember(frames) { mutableStateOf(false) }
    val canRotate = frames.isNotEmpty() && !failed
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(duck.fromNickname, style = DuckType.bodyLarge, color = c.textPrimary, maxLines = 1)
                Text(duck.kind.name, style = DuckType.bodySmall, color = c.textSecondary, maxLines = 1)
            }
            RarityTag(duck.kind.rarity)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(PonyStage),
            contentAlignment = Alignment.Center,
        ) {
            if (canRotate) {
                PonyTurntable(frames, Modifier.fillMaxSize()) { failed = true }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DuckImage(duck.kind, 220.dp)
                    Text("돌려 보는 이미지가 아직 없어요", style = DuckType.labelMedium, color = c.textSecondary)
                }
            }
        }
        if (canRotate) {
            Text("손가락으로 돌려 보세요", style = DuckType.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        BasicButton("닫기", onClose, Modifier.fillMaxWidth(), filled = false, height = 84.dp)
    }
}

// ---------------- 설정 ----------------

@Composable
fun SettingsPanel(state: DuckState, controller: DuckController, model: MyCarModel, onChooseModel: (String?) -> Unit) {
    val c = duckColors
    val driving = state.driveMode == DriveMode.DRIVING
    var text by remember(state.nickname) { mutableStateOf(state.nickname) }
    var result by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 내 차종: 차대번호로 인식한 값 또는 직접 선택한 값
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("내 차종", style = DuckType.bodyLarge, color = c.textPrimary)
            Text(model.label, style = DuckType.bodyMedium, color = c.textPrimary)
            when (model.source) {
                ModelSource.MANUAL -> "직접 선택"
                ModelSource.SYSTEM, ModelSource.SYSTEM_NAME -> "차량 정보로 인식"
                ModelSource.VIN_TABLE, ModelSource.VIN_CODE -> "차대번호로 인식"
                ModelSource.NONE -> null
            }?.let { Text(it, style = DuckType.labelMedium, color = c.textSecondary) }
            if (driving) {
                Text("주행 중에는 바꿀 수 없어요", style = DuckType.bodySmall, color = c.textSecondary)
            } else {
                BasicButton(
                    if (picking) "닫기" else "차종 직접 선택", onClick = { picking = !picking },
                    modifier = Modifier.fillMaxWidth(), filled = false, height = 84.dp,
                )
                if (picking) {
                    CarModelCatalog.all.forEach { m ->
                        val selected = model.source == ModelSource.MANUAL && model.key == m.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(if (selected) c.surfaceAccent else c.basic00)
                                .clickable { onChooseModel(m.id); picking = false }.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.label, style = DuckType.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            if (selected) Text("선택됨", style = DuckType.labelMedium, color = c.textSecondary)
                        }
                    }
                    BasicButton(
                        "자동 인식으로 되돌리기", onClick = { onChooseModel(null); picking = false },
                        modifier = Modifier.fillMaxWidth(), filled = false, height = 72.dp,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("내 닉네임", style = DuckType.bodyLarge, color = c.textPrimary)
            OutlinedTextField(
                value = text, onValueChange = { text = it; result = null },
                enabled = !driving, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = DuckType.bodyMedium.copy(color = c.textPrimary),
                placeholder = { Text("예: 히힝이", style = DuckType.bodyMedium, color = c.textTertiary) },
            )
            if (driving) {
                // 주행 중에는 키보드 입력을 쓰지 않는다
                Text("주행 중에는 바꿀 수 없어요", style = DuckType.bodySmall, color = c.textSecondary)
            } else {
                BasicButton(
                    "저장", onClick = {
                        result = when (controller.setNickname(text)) {
                            NicknameCheck.OK -> "저장했어요"
                            NicknameCheck.EMPTY -> "닉네임을 입력해 주세요"
                            NicknameCheck.TOO_LONG -> "10자까지 쓸 수 있어요"
                            NicknameCheck.BANNED -> "쓸 수 없는 단어가 들어 있어요"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), height = 84.dp,
                )
                result?.let { Text(it, style = DuckType.bodySmall, color = c.textPrimary) }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("주행 중 알림", style = DuckType.bodyLarge, color = c.textPrimary)
                Text("끄면 조용히 도감에만 쌓여요", style = DuckType.labelMedium, color = c.textSecondary)
            }
            Switch(checked = state.settings.notifyWhileDriving, onCheckedChange = controller::setNotifyWhileDriving)
        }
    }
}
