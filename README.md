# Duck Duck Beep!

**같은 차종끼리 오리를 주고받는 서비스** — 차 안에서 오리를 가득 담은 상자를 보는 듯한 경험을 주고, 같은 차를 마주치면 오리를 던져 가볍게 인사를 건네는 Pleos Connect 차량용 앱입니다. (현대자동차그룹 차량용 앱 개발 공모전 출품작)

| 항목 | 내용 |
|---|---|
| 앱 이름 | Duck Duck Beep! |
| 플랫폼 | Pleos Connect (Android Automotive OS 기반) |
| 언어 / UI | Kotlin, Jetpack Compose |
| minSdk / targetSdk | 28 / 37 (compileSdk 37) |
| 패키지 | `com.example.chargesuggest` (초기 프로젝트 이름이 남아 있으며, 앱 이름과는 무관) |

## APK 위치

- **`apk/서유진_DuckDuckBeep_release.apk`** (release 빌드, 약 12MB)
- 파일명은 `팀명_앱이름_release.apk` 규칙을 따르되, 파일명에 쓸 수 없는 공백과 `!`는 뺐습니다.
- 공모전 제출용으로 별도 키 없이 설치되도록 Android 기본(debug) 서명 키로 서명했습니다(스토어 배포 때는 전용 키로 교체).

## 실행 방법

### 1) APK로 바로 실행 (에뮬레이터)

```bash
adb install -r apk/서유진_DuckDuckBeep_release.apk
adb shell am start -n com.example.chargesuggest/.DuckDuckActivity
```

Pleos Connect 에뮬레이터는 사용자(user)가 여러 개라서, 위 명령이 현재 사용자에 설치되지 않으면 `--user 10`을 붙여 실행하세요.
설치 후에는 에뮬레이터 앱 목록에서 "Duck Duck Beep!"을 눌러도 됩니다.

### 2) 소스에서 빌드

필요: Android Studio(최신)와 JDK 17 이상(Android Studio에 포함된 JBR 사용 가능), 인터넷 연결(Pleos SDK를 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받습니다).

```bash
./gradlew testDebugUnitTest      # 단위 테스트
./gradlew installDebug           # 연결된 에뮬레이터에 debug 빌드 설치
./gradlew assembleRelease        # release APK: app/build/outputs/apk/release/
```

처음 열면 Android Studio가 `local.properties`(SDK 위치)를 자동으로 만듭니다.

## 테스트 환경 및 필요한 설정

- **에뮬레이터**: Pleos Connect Emulator **v2.1.0**, 시스템 이미지 Android **API 34** (Pleos Connect v2.1.0 APIs)
- **AVD 설정**: Form Factor Automotive, Default boot Cold, RAM 4GB
- **화면 규격**: 2560 × 1440, 160dpi (심사 기준 화면). Portrait(841 × 1210dp)와 Landscape(1682 × 1210dp) 모두 대응하며, 기준은 방향이 아니라 창 너비입니다.
- **권한**: 공모전 개발 단계 기준으로 `AndroidManifest.xml`의 `pleos.car.permission.*` 선언만으로 동작합니다(별도 승인 불필요).
- 일반 휴대폰 에뮬레이터가 아니라 **Pleos Vehicle Service가 들어 있는 Pleos Connect AVD**여야 차량 값(기어·조향각·브레이크 등)을 읽을 수 있습니다.

## 시연 방법 — "시연 모드" 리모콘

서버와 실제 같은 차종 차량이 없어도 핵심 흐름을 볼 수 있도록, 앱 화면 **오른쪽 아래 "시연 모드" 버튼**을 누르면 리모콘이 열립니다. (앱을 켤 때마다 닉네임 정하기부터 시작합니다.)

| 버튼 | 동작 |
|---|---|
| 주행 | 주행 상태로 바꿉니다. 오리 상자가 출발·회전·브레이크·요철에 따라 쏠리고 튑니다(출발 약 3초 뒤 방지턱) |
| 정차 | 정차 상태로 바꿉니다. 오리들이 앞으로 쏠렸다가 가라앉습니다 |
| 근처 차 발견 | 근처에 같은 차종이 있는 상태를 켜고 끕니다. **화면에는 아무 알림이 뜨지 않고**, 이 상태에서 오리를 던져야 전달됩니다 |
| 오리 받기 | 다른 차가 보낸 오리를 받습니다(우산 → 눈사람 → 선글라스 → 우비 → 목도리 → 무지개 순) |
| 처음으로 | 저장된 값을 지우고 처음(닉네임 정하기)부터 다시 시작합니다 |

### 추천 시연 순서

1. 닉네임을 정하고 [시작하기] → 오리 10마리가 상자에 쏟아집니다. 튜토리얼(던지기 → 받은 오리 확인 → 도감)을 따라 합니다.
2. 리모콘 **주행** → 오리 상자가 차의 움직임에 따라 움직입니다.
3. **근처 차 발견**을 켜고, 오리를 집어 위로 던집니다 → "보낼까요?" → [보내기].
4. **오리 받기** → 왼쪽에서 빛나는 오리가 굴러 들어옵니다. 눌러서 확인하면 도감에 기록됩니다.
5. 오른쪽 위 도감·설정 아이콘으로 도감과 설정(알림·조명 색·닉네임 셔플)을 확인합니다.

가상 콘솔(Virtual Console)에서 기어, 브레이크 페달, 조향각을 바꿔도 같은 반응을 확인할 수 있습니다.

## 사용한 Pleos SDK·API

| 기능 | 사용한 자원 |
|---|---|
| 주행·정차·주차 판별 | Vehicle SDK `Gear` + AAOS `CarUxRestrictionsManager` |
| 상자 속 오리의 쏠림·튐 | Vehicle SDK `SteeringWheel`(조향각), `Brake`(페달) |
| 보내고 받을 때 조명 | Vehicle SDK `Light`(앰비언트) |
| 같은 차종 판별 | AAOS `CarInfoManager`(제조사·모델명) |

NaviHelper 의존성은 선언되어 있으나 핵심 기능에서는 사용하지 않습니다. 위치 기반 매칭과 날씨 연동은 1차에서는 목업으로 대신합니다(아래 참고).

## 구조

```
app/src/main/java/com/example/chargesuggest/
  duckduck/domain/    # 규칙(SDK 없이 단위 테스트 가능): 상자, 던지기, 날씨 오리, 닉네임
  duckduck/physics/   # 직접 만든 2D 물리(오리 1마리 = 원 4개 강체)와 차의 움직임 변환
  duckduck/           # 상태 기계(컨트롤러)와 ViewModel
  ui/duckduck/        # Jetpack Compose 화면(메인 상자, 카드, 튜토리얼, 도감, 설정)
  pleos/ duck/        # Pleos SDK·AAOS 연결
```

## 1차 제출 범위와 한계

- 구현: 닉네임 정하기, 오리 상자(물리·집기·던지기·차 움직임 반응), 오리 보내기·받기, 날씨 오리 도감, 설정, 튜토리얼, 시연 리모콘
- 목업(2차 예정): 서버 매칭, 실제 위치, 기상청 날씨 API
- 조향각의 부호와 앰비언트 임의 색의 실제 출력은 가상 콘솔·실차에서의 확인이 더 필요합니다.
- 제3자 아이콘 라이선스는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요.
