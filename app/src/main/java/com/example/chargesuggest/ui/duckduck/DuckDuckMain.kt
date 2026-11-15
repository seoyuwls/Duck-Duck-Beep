package com.example.chargesuggest.ui.duckduck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.chargesuggest.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargesuggest.duckduck.Card
import com.example.chargesuggest.duckduck.DuckDuckController
import com.example.chargesuggest.duckduck.DuckDuckState
import com.example.chargesuggest.duckduck.SendOutcome
import com.example.chargesuggest.duckduck.domain.BookEntry
import com.example.chargesuggest.duckduck.domain.DuckType
import com.example.chargesuggest.duckduck.domain.Josa
import com.example.chargesuggest.duckduck.domain.MainLayout
import com.example.chargesuggest.duckduck.domain.TicketView
import com.example.chargesuggest.ui.duck.DuckType as Type
import com.example.chargesuggest.ui.duck.PonyTurntable
import com.example.chargesuggest.ui.duck.duckColors
import com.example.chargesuggest.ui.duck.pressScale

/*
 * 주정차(정차·주차)의 기본 화면(메인). 와이어프레임(1682×1210 Landscape)을 직접 측정한 값으로 만들었다.
 *
 *   바깥 여백   좌우 40 / 위 40 / 아래 48            카드 모서리 24, 안쪽 여백 40, 카드 사이 24
 *   머리글      높이 64 (왼쪽 앱 이름, 오른쪽 도감·설정 아이콘) -> 33 간격 -> 인사말 2줄(줄 높이 52) -> 40 간격 -> 카드 영역
 *   왼쪽 카드   "오늘의 오리" 너비 600(37%) — 위치 고정, 오리 이미지는 카드 안쪽 너비(520)까지, 3D 회전 이미지가 있으면 돌려 볼 수 있음
 *   오른쪽      너비 961(63%): 통계 카드 둘(높이 184 고정) + 목록 카드 둘(남은 높이를 반반, 내용이 더 필요한 쪽이 커짐)
 *               위가 "오늘 마주친 ○○", 아래가 "오늘 받은 오리". 목록 카드는 제목만 있고 부제는 없다.
 *   글자        인사말 Title Medium 36 / 카드 제목 Title Small 32 굵게 / 부제·라벨·행 Label Medium 24 / 통계 숫자 Title Large 40
 *
 * 목록은 주행 중 생긴 이벤트가 줄 서는 곳이다(규칙은 MainLayout 참고): 최대 2개, 처리하면 사라지고, 새 알림은 아래로 붙는다.
 * 와이어프레임과 다른 점: 회색 글자는 대비 4.5:1(가이드)을 지키려고 한 단계 진한 회색(basic_500)을 쓴다.
 */

internal enum class ParkedScreen { MAIN, COLLECTION, SETTINGS }

@Composable
internal fun SubScreen(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val c = duckColors
    // 도감·설정 공통 화면: 흰 바탕에 뒤로 가기(<)와 제목, 아래로 내용
    Column(modifier.fillMaxSize().background(c.basic00).padding(top = 40.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "뒤로" },
                contentAlignment = Alignment.Center,
            ) { PhIcon(R.drawable.ph_caret_left, c.textPrimary, size = 40.dp) }
            Text(title, style = Type.titleMedium, color = c.textPrimary, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 24.dp)) { content() }
    }
}

// ---------------- 메인 ----------------

@Composable
internal fun MainScreen(
    state: DuckDuckState,
    controller: DuckDuckController,
    wide: Boolean,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = duckColors
    var notice by remember { mutableStateOf<String?>(null) }
    val greetingName = state.nickname?.text ?: state.modelName

    val onSend: (Int) -> Unit = { id ->
        notice = when (val r = controller.sendTicket(id)) {
            is SendOutcome.Sent -> null
            SendOutcome.DeliveryFailed -> "오리를 보내지 못했어요. 상대가 앱을 껐을 수 있어요"
            SendOutcome.NoNickname -> "닉네임을 먼저 정해 주세요"
            SendOutcome.NotAllowedWhileMoving -> "멈춘 뒤에 보낼 수 있어요"
            is SendOutcome.Rejected -> "지금은 보낼 수 없어요 (${r.result})"
            SendOutcome.NoNearbyCar, SendOutcome.NotInBin -> "지금은 보낼 수 없어요"
        }
    }
    val onConfirm: (Int) -> Unit = { id -> controller.openDuck(id) }

    Column(modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 48.dp)) {
        MainHeader(state.appName, showIcons = true, onCollection, onSettings)
        Spacer(Modifier.height(33.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "${greetingName}님!\n오늘도 함께 달려요",
                style = Type.titleMedium.copy(lineHeight = 52.sp), color = c.textPrimary, modifier = Modifier.weight(1f),
            )
            notice?.let { Text(it, style = Type.bodySmall, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(40.dp))
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                TodayDuckCard(state, Modifier.weight(600f).fillMaxHeight())
                RightColumn(state, onSend, onConfirm, Modifier.weight(961f).fillMaxHeight())
            }
        } else {
            // Portrait(841)는 와이어프레임이 아직 없어서 위에서 아래로 쌓는 임시 배치다 [팀 확인]
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                StatsRow(state, Modifier.fillMaxWidth().height(150.dp))
                TodayDuckCard(state, Modifier.weight(1.3f).fillMaxWidth())
                EncounterCard(state, onSend, Modifier.weight(1f).fillMaxWidth())
                ReceivedCard(state, onConfirm, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** 오른쪽 열: 통계 카드 둘(높이 고정) + 목록 카드 둘(남은 높이 분배) */
@Composable
private fun RightColumn(state: DuckDuckState, onSend: (Int) -> Unit, onConfirm: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val statsH = 184.dp
        val gap = MainLayout.CARD_GAP.dp
        // 두 목록 카드가 나눠 쓰는 높이 (통계 카드와 카드 사이 간격 2개를 뺀 나머지)
        val avail = (maxHeight - statsH - gap - gap).value
        val enc = MainLayout.visibleRows(MainLayout.pendingEncounters(state.today))
        val recv = MainLayout.visibleRows(MainLayout.pendingReceived(state.book.entries))
        val (h1, h2) = MainLayout.splitHeights(
            avail = avail,
            need1 = MainLayout.cardNeed(enc.size).toFloat(),
            need2 = MainLayout.cardNeed(recv.size).toFloat(),
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            StatsRow(state, Modifier.fillMaxWidth().height(statsH))
            EncounterCard(state, onSend, Modifier.fillMaxWidth().height(h1.dp))
            ReceivedCard(state, onConfirm, Modifier.fillMaxWidth().height(h2.dp))
        }
    }
}

// ---------------- 머리글 ----------------

@Composable
internal fun MainHeader(appName: String, showIcons: Boolean, onCollection: () -> Unit = {}, onSettings: () -> Unit = {}) {
    val c = duckColors
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(appName, style = Type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        if (showIcons) {
            // 두 아이콘 사이는 시안 기준 약 48px(40dp): 64dp 칸의 안쪽 여백 16dp + 칸 사이 24dp
            Row(horizontalArrangement = Arrangement.spacedBy(HEADER_ICON_GAP)) {
                IconSlot("도감", onCollection) { PhIcon(R.drawable.ph_book_bookmark, HeaderIconColor) }
                IconSlot("설정", onSettings) { PhIcon(R.drawable.ph_gear, HeaderIconColor) }
            }
        }
    }
}

/** 머리글 도감·설정 아이콘 칸 사이 간격 */
internal val HEADER_ICON_GAP = 24.dp

/** 머리글 아이콘 색: 시안대로 연한 회색(basic_400). 아이콘 대비 4.5:1(가이드)에는 못 미친다 — 팀 확인 필요 */
private val HeaderIconColor @Composable get() = duckColors.basic400

/** 64dp 터치 영역 안에 40dp 아이콘. 글자 라벨은 와이어프레임에 없어서 접근성 설명으로만 둔다 [가이드: 아이콘+라벨 병기 권장] */
@Composable
private fun IconSlot(description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/**
 * Phosphor 아이콘(res/drawable/ph_*.xml)을 색만 바꿔 그린다. 기본 48dp는 와이어프레임의 아이콘 크기와 같다
 * (도감 33×39, 설정 39×39). 라이선스는 THIRD_PARTY_NOTICES.md.
 */
@Composable
internal fun PhIcon(@androidx.annotation.DrawableRes res: Int, tint: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

// ---------------- 카드들 ----------------

@Composable
private fun Modifier.card(): Modifier {
    val c = duckColors
    return this.clip(RoundedCornerShape(24.dp)).background(c.basic00).padding(MainLayout.CARD_PADDING.dp)
}

private val cardTitleStyle
    @Composable get() = Type.titleSmall.copy(fontWeight = FontWeight.Bold, lineHeight = MainLayout.TITLE_LINE.sp)

/** 왼쪽: 오늘의 오리. 위치 고정. 3D 회전 이미지(assets/duck/turntable)가 있으면 손가락으로 돌려 볼 수 있다 */
@Composable
private fun TodayDuckCard(state: DuckDuckState, modifier: Modifier) {
    val c = duckColors
    val type = state.previewDuck ?: DuckType.BASIC
    Column(modifier.card()) {
        Text("오늘의 오리", style = cardTitleStyle, color = c.textPrimary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text("날씨에 따라 종류가 달라져요", style = Type.labelMedium.copy(lineHeight = 34.sp), color = c.textSecondary, maxLines = 1)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 이미지는 카드 안쪽 너비까지(와이어프레임에서 520), 낮은 창에서는 라벨 자리를 빼고 줄인다
            val imageSize = minOf(maxWidth, (maxHeight - 106.dp).coerceAtLeast(120.dp))
            // 와이어프레임 측정값에 맞춰 오리와 라벨을 15dp 아래로 (오리 위쪽 521, 라벨 중심 1073)
            Column(Modifier.padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                DuckHero(type, imageSize)
                Spacer(Modifier.height(70.dp))
                Text(type.label, style = Type.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun DuckHero(type: DuckType, size: Dp) {
    val context = LocalContext.current
    val frames = remember(type) { duckTurntableFrames(context, type.id) }
    if (frames.isNotEmpty()) {
        PonyTurntable(frames, Modifier.size(size), description = "${type.label} 회전 보기")
    } else {
        DuckTypeImage(type, size)
    }
}

@Composable
private fun StatsRow(state: DuckDuckState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(MainLayout.CARD_GAP.dp)) {
        StatCard("받은 오리 수", state.book.entries.size, Modifier.weight(1f).fillMaxHeight())
        StatCard("내가 준 오리 수", state.sentCount, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 높이 고정 통계 카드: 라벨 -> 14 간격 -> 큰 숫자 */
@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier) {
    val c = duckColors
    Column(modifier.card()) {
        Text(label, style = Type.labelMedium, color = c.textSecondary)
        Spacer(Modifier.height(14.dp))
        Text(value.toString(), style = Type.titleLarge, color = c.textPrimary)
    }
}

/** 목록 카드 공통 틀: 제목만 있고(부제 없음), 줄이 없으면 아래 영역 가운데에 빈 문구를 둔다 */
@Composable
private fun ListCard(
    title: String, empty: Boolean, emptyText: String, modifier: Modifier,
    rows: @Composable ColumnScope.() -> Unit,
) {
    val c = duckColors
    Column(modifier.card()) {
        Text(title, style = cardTitleStyle, color = c.textPrimary, maxLines = 1)
        if (empty) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(emptyText, style = Type.labelMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            Spacer(Modifier.height(MainLayout.GAP_TO_ROWS.dp))
            Column(verticalArrangement = Arrangement.spacedBy(MainLayout.ROW_GAP.dp), content = rows)
        }
    }
}

/** 오늘 마주친 ○○: [시간] [익명의 ○○] [보내기]. 보낸 만남과 15분이 지난 만남은 사라진다 */
@Composable
private fun EncounterCard(state: DuckDuckState, onSend: (Int) -> Unit, modifier: Modifier) {
    val model = state.modelName
    val rows: List<TicketView> = MainLayout.visibleRows(MainLayout.pendingEncounters(state.today))
    ListCard("오늘 마주친 $model", rows.isEmpty(), "아직 마주친 ${Josa.subject(model)} 없어요", modifier) {
        rows.forEach { v ->
            ListRow(MainLayout.relativeTime(v.ticket.createdAtMs, state.nowMs), "익명의 $model") {
                RowButton("보내기") { onSend(v.ticket.id) }
            }
        }
    }
}

/** 오늘 받은 오리: [시간] [보낸 차 닉네임] [확인하기]. 확인한 오리는 사라진다 */
@Composable
private fun ReceivedCard(state: DuckDuckState, onConfirm: (Int) -> Unit, modifier: Modifier) {
    val rows: List<BookEntry> = MainLayout.visibleRows(MainLayout.pendingReceived(state.book.entries))
    ListCard("오늘 받은 오리", rows.isEmpty(), "아직 받은 오리가 없어요", modifier) {
        rows.forEach { e ->
            ListRow(MainLayout.relativeTime(e.receivedAtMs, state.nowMs), e.fromNickname) {
                RowButton("확인하기") { onConfirm(e.id) }
            }
        }
    }
}

/** 한 줄(높이 63): 시간 칸(96) + 이름 + 오른쪽 끝 버튼 */
@Composable
private fun ListRow(time: String, name: String, trailing: @Composable () -> Unit) {
    val c = duckColors
    Row(Modifier.fillMaxWidth().height(MainLayout.ROW_H.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = Type.labelMedium, color = c.textSecondary, modifier = Modifier.width(96.dp), maxLines = 1)
        Text(name, style = Type.labelMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

/** 줄 오른쪽의 작은 버튼 (높이 63, 가로 여백 18). 누르면 95%로 줄어든다 */
@Composable
private fun RowButton(label: String, onClick: () -> Unit) {
    val c = duckColors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.height(MainLayout.ROW_H.dp).pressScale(source).clip(RoundedCornerShape(16.dp)).background(c.buttonFilled)
            .clickable(interactionSource = source, indication = null, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onButtonFilled, maxLines = 1) }
}
