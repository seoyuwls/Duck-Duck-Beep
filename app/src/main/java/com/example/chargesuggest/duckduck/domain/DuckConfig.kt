package com.example.chargesuggest.duckduck.domain

/**
 * Duck Duck 앱의 규칙 상수 (duck-app-spec.md 4장). 숫자를 바꿀 때는 이 파일만 고친다.
 * `제안값`은 명세에서 팀 확인이 필요하다고 한 값, `명세 밖 제안`은 명세에 없는데 빈칸이라 우리가 채운 값이다.
 */
object DuckConfig {
    /** 개발용 전체 시연 패널(날씨·차 움직임·같은 차 두기 등 버튼 많음)을 쓸지. false(기본)면 네 개짜리 시연 리모콘만 보인다 */
    const val FULL_DEMO_PANEL = false

    /**
     * 시연용: 닉네임 정하기와 튜토리얼을 주행 중에도 진행할 수 있게 한다(시연 리모콘으로 주행을 눌러도 흐름이 끊기지 않게).
     * false면 닉네임은 주차(P)에서만 정하고, 튜토리얼은 주행 중에는 숨긴다(안전 규칙).
     */
    const val ONBOARD_WHILE_DRIVING = true

    /** 앱 이름(화면 머리글·런처). 런처 이름은 res/values/strings.xml의 app_name과 같게 둔다 */
    const val APP_NAME = "Duck Duck Beep!"

    /** 메인 화면을 오리 통(물리로 움직이는 오리 더미)으로 보여 줄지. false면 이전의 대시보드형 메인 */
    const val BIN_MAIN_SCREEN = true

    /**
     * 주행 중에도 오리 통이 차의 움직임(출발·회전·브레이크·요철)에 따라 쏠리고 튀는지.
     * 가이드는 주행 중 시선을 끄는 움직임을 금지한다. 터치가 없고 상태 문구가 위 카드로 따로 있지만 심사에서 문제가 되면 false로 끈다(주행 중 오리는 가만히 있는다).
     */
    const val DRIVE_MOTION_WHILE_DRIVING = true

    /**
     * 주행 중에도 손가락으로 오리를 집어 움직일 수 있는지(오리를 건네는 것은 정차·주차에서만).
     * 가이드는 주행 중 단순 조작만 허용하고 시선 분산을 피하라고 한다. 심사에서 문제가 되면 false로 끈다.
     */
    const val DRIVING_DUCK_TOUCH = true

    /**
     * 근처에 같은 차가 있을 때 시스템이 먼저 알려 주는지(소리·조명·"발견했어요" 카드).
     * false(현재 기획): 시스템은 알리지 않는다. 운전자가 같은 차를 발견하고 오리를 던졌을 때, 근처에 같은 차가 있으면 전달되고 없으면 아무 일도 없다.
     */
    const val NOTIFY_NEARBY_CARS = false

    /**
     * 받은 오리 확인 카드("좋은 하루 되세요!")가 자동으로 사라지지 않고 [확인] 버튼을 눌러야 닫히는지.
     * false면 RESULT_CARD_MS 뒤에 저절로 넘어간다(이전 방식).
     */
    const val RESULT_NEEDS_CONFIRM = true

    /** "이 오리를 보낼까요?" 확인 카드를 이만큼 두고 답이 없으면 닫는다(오리는 통으로 돌아온다). 운전 중에 카드가 계속 떠 있지 않게 한다 */
    const val SEND_CONFIRM_TIMEOUT_MS = 10_000L

    /**
     * 정차·주차가 되면 받은 오리를 자동으로 확인 카드에 띄우는지. false(현재 기획): 받은 오리 표시가 있는 오리를 사용자가 직접 눌러야 확인된다.
     */
    const val AUTO_OPEN_RECEIVED = false

    /**
     * 설정 "오리 알림"의 처음 값: 오리를 보내거나 받을 때 꽥! 소리를 낼지(true). 카드가 뜨거나 확인할 때는 소리가 나지 않는다.
     * 오리를 눌렀을 때의 꽥 소리(DuckSqueaker)는 이 설정과 상관없이 항상 난다.
     */
    const val PLAY_REACTION_SOUND = true

    /**
     * 차량이 차종을 알려 주지 않을 때(에뮬레이터는 "Connect"처럼 목록에 없는 이름을 준다) 화면과 닉네임에 쓸 시연용 차종.
     * 비워 두면("") 브랜드 이름("현대")을 쓴다. 실제 차에서 차종이 인식되면(차량 정보·차대번호·직접 선택) 그 값을 쓴다.
     */
    const val DEMO_CAR_MODEL_LABEL = "기아 쏘렌토"
    const val DEMO_CAR_MODEL_KEY = "kia_sorento"

    /**
     * 날씨 오리 선물: 하루에 한 번, 그 순간 날씨가 특별한 오리(눈사람·우비·우산·선글라스·목도리·무지개)에 해당하면 확률로만 통에 들어온다.
     * 기회는 하루 한 번이라 성공하든 못하든 그날은 끝난다. 확률은 팀 확인 필요(희귀하게).
     */
    const val WEATHER_GIFT_CHANCE = 0.2f

    /** 무지개 오리는 비가 그친 뒤 1시간 안에만 가능해서 조건이 이미 까다롭다. 이 확률도 팀 확인 필요 */
    const val RAINBOW_GIFT_CHANCE = 0.35f

    /** 날씨 선물로 들어온 오리를 확인할 때 카드에 보이는 보낸 이("오늘의 날씨 · 눈사람 오리") */
    const val GIFT_SENDER_LABEL = "오늘의 날씨"

    /** 시작 통에 눈사람 오리와 우산 오리를 한 마리씩 섞을지(시연용). false가 기본: 시작은 기본 오리로만 */
    const val STARTER_SPECIAL_DUCKS = false

    /** 오리 아래 배경에 부드러운 그림자를 그릴지 */
    const val DUCK_SHADOW = false

    /** 주행 중에도 도감·설정을 열고 설정(알림·조명·닉네임)을 바꿀 수 있게 할지. 주행 중 설정 화면이 열려 있는데 조작이 안 되는 일을 막는다 */
    const val COLLECTION_SETTINGS_WHILE_DRIVING = true

    /** 앱을 시작할 때마다 저장된 값을 지우고 닉네임 정하기부터 시작할지(시연·심사용. 정식 출시에서는 false) */
    const val FRESH_START_EVERY_LAUNCH = true

    /** 처음 시작할 때 메인 화면에서 기능을 하나씩 짚어 주는 튜토리얼(3단계)을 보여 줄지 */
    const val TUTORIAL_ENABLED = true

    /** 튜토리얼 2단계에서 왼쪽으로 굴러 들어오는 첫 오리를 보낸 이로 표시할 이름 */
    const val TUTORIAL_SENDER_LABEL = "오리 친구"

    /**
     * 주행 중에도 받은 오리를 눌러 확인할 수 있는지(정차 때와 같은 확인 카드가 뜬다).
     * 가이드는 주행 중 시선을 끄는 정보를 피하라고 한다. 심사에서 문제가 되면 false로 끈다(주행 중에는 받은 오리가 쌓이기만 하고 정차 후 확인한다).
     */
    const val REVEAL_RECEIVED_WHILE_DRIVING = true

    /** 오리 통에 담을 수 있는 오리 수(처음에도 이만큼 기본 오리로 가득 채워서 시작한다). 오리가 너무 작고 많아 보여서 10마리로 줄이고 크게 그린다 */
    const val BIN_CAPACITY = 10

    /** 같은 차를 감지하는 반경(m). 제안값 */
    const val DETECT_RADIUS_M = 300

    /** 티켓 유효 시간: 만남 시점부터 15분 */
    const val TICKET_VALID_MS = 15 * 60_000L

    /** 하루에 오리를 보낼 수 있는 횟수. 제안값 */
    const val DAILY_SEND_LIMIT = 20

    /** 같은 상대(차)에게 하루에 보낼 수 있는 횟수. 명세 밖 제안: 티켓은 15분 뒤 새로 생기므로 한 상대에게 하루 종일 보낼 수 있어서 막는다 */
    const val SEND_PER_RECIPIENT_PER_DAY = 1

    /** 서버가 위치를 보관하는 시간 */
    const val LOCATION_KEEP_MS = 15 * 60_000L

    /** 주행 중 카드가 자동으로 닫히는 시간 (2~3초) */
    const val MOVING_CARD_MS = 3_000L

    /** "오리가 출발했어요" 완료 화면이 자동으로 넘어가는 시간 */
    const val SENT_CARD_MS = 3_000L

    /** 오리 확인 결과 화면이 자동으로 넘어가는 시간 (다음 받은 오리가 있으면 이어서 보여 준다) */
    const val RESULT_CARD_MS = 3_000L

    /**
     * 주행 중 "찾는 중" 화면의 원이 와이파이가 켜지듯 움직이는지.
     * 가이드는 주행 중 시선을 끄는 애니메이션을 금지하지만, 이것은 상태("찾는 중")를 알리는 느린 로딩 표시라서 허용으로 본다
     * (디자인 가이드 3.6 "애니메이션은 상태를 전달하기 위해 사용"). 심사에서 문제가 되면 false로 끈다.
     */
    const val SEARCH_ANIMATION_WHILE_DRIVING = true

    /** 정차로 보려면 정차 상태가 이만큼 이어져야 한다. 명세 밖 제안: 서행 중 버튼이 깜빡이지 않게 */
    const val STOP_DWELL_MS = 2_000L

    /** 같은 종류의 알림(발견·받음) 사이 최소 간격. 명세 밖 제안: 같은 차종이 많은 길에서 꽥 소리가 반복되지 않게 */
    const val ALERT_MIN_INTERVAL_MS = 3 * 60_000L

    /** 날씨를 새로 조회하는 주기 */
    const val WEATHER_REFRESH_MS = 30 * 60_000L

    /** 비가 그친 뒤 무지개 오리가 나오는 시간 */
    const val RAINBOW_WINDOW_MS = 60 * 60_000L

    /** 기온 기준(℃): 이상이면 선글라스, 이하이면 목도리 */
    const val HOT_C = 33.0
    const val COLD_C = 0.0

    /** 설정에서 고를 수 있는 알림 조명 색(0xRRGGBB): 노랑(기본), 흰색, 파랑, 초록, 주황. 놀랄 수 있는 빨간 계열은 뺐다. 차가 임의 RGB를 그대로 내는지는 TODO(verify) */
    val AMBIENT_CHOICES = intArrayOf(0xFFC224, 0xFFFFFF, 0x0064FF, 0x02C265, 0xFF8A00)

    /** 앰비언트 조명 색(0xRRGGBB). 오리 노랑. 흰색은 기본 색과 같아 반응이 안 보여서 피한다 */
    const val AMBIENT_RGB = 0xFFC224

    /** 효과음 볼륨 0~1. 내비 안내 음성 중에는 작게 해서 안내가 우선되게 한다 */
    const val SOUND_VOLUME = 0.8f
    const val SOUND_VOLUME_NAVI = 0.3f

    /** 하루의 경계를 한국 시간 자정으로 본다 */
    fun kstDay(ms: Long): Long = (ms + 9 * 3_600_000L) / 86_400_000L
}
