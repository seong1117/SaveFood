# AGENTS.md — 세이프 푸드(SaveFood) 프로젝트 컨텍스트

이 문서는 프로젝트 배경과 지금까지의 결정 사항을 정리한 것입니다.
작업을 시작하기 전에 먼저 읽고, 아래 "규칙"을 지켜 주세요.

## 1. 프로젝트 개요

- 팀 프로젝트(대학 과제)로 만드는 **식재료 소비기한 관리 + 맞춤 레시피 앱**입니다.
- 핵심 가치: 보관 식품과 기한을 한눈에 확인하고, 임박 재료로 만든 레시피 추천을 받으며, 남은 음식까지 안전하게 보관·소비합니다.
- 타깃: 1인 가구, 자취생, 사회초년생.
- 차별점: 상태 정보 제공(날짜 추적을 넘어 상태 판단 기준 제시), 자동 소비기한 설정(식약처 권장 기한), 간결한 사용자 흐름(등록→레시피→소진).
- 내 담당(이 저장소 관리자): **GitHub 관리, UI 구현, 로그인 등 계정 관리**. 다른 팀원은 이 프로토타입을 바탕으로 기능을 추가합니다.

## 2. 기술 스택과 제약

- **언어: Java 17** (Kotlin 아님. 기획안에 Java로 명시되어 있으므로 Kotlin으로 바꾸지 말 것)
- Android Studio, Gradle(Groovy) 8.7, AGP 8.5.2, compileSdk/targetSdk 34, minSdk 26
- UI: XML 레이아웃 + Material Components(1.12), Fragment + BottomNavigationView, RecyclerView
- Firebase: Authentication(이메일/비밀번호), Cloud Firestore (BoM 33.1.2)
- CameraX 1.3.4 + ML Kit 한국어 텍스트 인식(OCR, 영수증) + ML Kit 커스텀 이미지 라벨링(식재료 사진)
- 바코드 스캔은 팀 회의로 제외함(구현 대비 실사용 효율 낮음)
- 빌드 JDK는 17~21 (Gradle 8.7은 JDK 25에서 동작하지 않음. Android Studio Gradle JDK를 jbr-21 등으로 지정)
- Compose, ViewBinding, Hilt 등은 사용하지 않음. 새로 도입하기 전에 먼저 물어볼 것.
- 앱 패키지: `com.savefood.app`

## 3. 현재 상태

- 프로토타입이 생성되어 있고, **한 번도 컴파일해 보지 않았음**(생성 환경에 Android SDK 없음). XML·참조·Java 문법만 정적으로 검사함.
  - 첫 작업은 Gradle Sync와 빌드를 성공시키는 것. 오류가 나면 최소한으로 수정하고 무엇을 바꿨는지 알려 줄 것.
- 데이터는 `data/FoodRepository.java`의 **더미 데이터**(앱 종료 시 초기화). Firestore 저장은 미구현.
- `app/google-services.json`은 아직 없을 수 있음. 없어도 빌드·실행되며, 로그인 화면의 "로그인 없이 둘러보기"로 진입함. (`app/build.gradle`에서 파일이 있을 때만 google-services 플러그인을 적용)
- 식품 썸네일은 이모지로 대체함(Figma 이미지 미적용).

## 4. 구조

```
app/src/main/java/com/savefood/app/
├─ MainActivity            하단 탭 4개(홈/스캔/레시피/설정), switchTab() 제공
├─ auth/                   LoginActivity(로그인·회원가입·비밀번호 재설정·게스트), AuthHelper
├─ model/                  FoodItem, Leftover, Recipe
├─ data/FoodRepository     더미 저장소(싱글톤). 시그니처 유지한 채 Firestore로 교체할 곳
├─ ui/home/                HomeFragment, FoodAdapter, LeftoverAdapter,
│                          FoodMenuBottomSheet, LeftoverBottomSheet
├─ ui/scan/                ScanFragment(모드: 식재료·가격표·영수증·소비기한 + 등록 폼, 소비기한은 ①날짜 ②식품명 두 번 촬영),
│                          ScanGuide(스캔 사용 가이드, "다시 보지 않기"까지 매번 표시),
│                          CameraController(CameraX 미리보기·촬영·탭 초점·플래시, 미리보기 영역만 잘라 OCR),
│                          FoodClassifier(식재료 사진 분류, assets/food_model.tflite + food_labels.txt),
│                          PriceTagParser(진열대 가격표 OCR → 큰 글자 품명 후보, 나뉜 줄 잇기·잡음 기호 제거), OcrPiece(OCR 줄 조각),
│                          ExpiryDateParser(포장지·라벨 OCR → 소비기한 후보, 여러 날짜 표기를 YYYY-MM-DD로, 단위 테스트 있음),
│                          ReceiptParser(영수증 OCR → 품명·수량·구매일, 단위 테스트 있음), FoodCatalog(품목별 이모지·보관 장소·기본 일수)
├─ ui/recipe/              RecipeFragment, RecipeAdapter, RecipeDetailActivity
├─ ui/settings/            SettingsFragment(계정 표시, 로그아웃)
└─ util/UiUtils
```

## 5. 화면 흐름과 설계 결정 (Figma 기준)

- 시작: `LoginActivity` → `MainActivity`
- **홈**: 임박 식품 카드, 남은 요리(가로 목록), 소비기한별 식품 목록(D-day 배지, 1일 이하는 빨강), 우측 하단 스캔 버튼
- **보관 기한 추적과 상태 정보는 홈 화면에서 항목을 눌렀을 때 뜨는 바텀시트 메뉴**로 제공함(별도 화면 아님)
  - 식품 시트: 보관 기한 진행 막대(등록일·현재·소비기한), 상태 칩(양호/주의/상함), "이 재료로 레시피 보기", 소비 완료, 수량·기한 수정, 폐기 처리
  - 남은 요리 시트: 안전 보관 기한 진행 막대, 임박 알림 스위치, **재조리 가이드**(가로 카드), "다 먹었어요", 보관 정보 수정, 폐기 처리
- **스캔/등록**: 네 가지 모드(촬영 또는 갤러리 사진). 식재료 = 포장 없는 식재료 사진을 분류해 후보 칩 → 식품명·기한·보관 장소 채움(모델 없거나 틀리면 "자주 사는 품목" 칩으로 바로 선택). 가격표 = 장 볼 때 진열대 가격표를 찍어 품명 후보 칩(가격·행사 문구 제외, 큰 글자 우선). 영수증 = OCR → 품명·수량 목록(가격 무시)을 골라 일괄 등록, 영수증 날짜는 등록일로 사용. 소비기한 = 포장지·라벨의 날짜를 읽어 기한 칸만 채움(영수증·가격표에는 기한이 없으므로 기한이 긴 품목은 따로 찍음, 제조일·포장일은 제외하고 "제조일로부터 N개월"은 계산). 결과는 모두 "추정" 표시 후 사용자가 확인. 보관 상태(양호/숙성 중/주의), 보관 장소(냉장실/냉동실/실온)
- **레시피**: 추천 목록(재료 N/M 보유, 임박 재료 칩) → 상세(재료 보유/부족, 조리 순서) → "조리 완료"
- **조리 완료 이후 흐름(미구현, 설계 확정)**: 사용한 재료 확인(수량 수정) → 재고에서 차감 → 남은 음식 보관 등록(남은 양, 보관 장소, 권장 소비기한 1/3/5일 기본 3일, 마감 전 알림) → 홈의 "남은 요리"에 표시

### 문구 규칙
- 식품명에는 "신선한" 같은 수식어를 넣지 않음(예: "연어 필렛 (200g)", "우유 (1L)").
- 날짜 예시는 하드코딩하지 말고 `LocalDate.now()` 기준으로 계산할 것.
- 화면 제목: "오늘의 레시피", 부제 "임박 재료로 만드는 추천 요리", "남은 음식 보관 등록", "권장 소비 기한".

## 6. 다음 작업 우선순위

1. Gradle Sync/빌드 성공, 에뮬레이터 실행 확인
2. Firebase 연결: `google-services.json` 추가, 이메일 로그인·회원가입·로그아웃 실기 테스트, Firestore 보안 규칙(`users/{uid}` 하위는 본인만 읽기/쓰기)
3. UI 다듬기: Figma와 비교해 간격·색·폰트 보정, 이모지를 실제 이미지로 교체할 구조 준비
4. `FoodRepository` → Firestore 연동 (`users/{uid}/foods`, `users/{uid}/leftovers`). `LocalDate`는 Firestore에 직접 저장되지 않으므로 문자열 또는 Timestamp로 변환
5. 조리 완료 흐름(위 5번 항목) 구현
6. 알림: 소비기한 임박 알림 + 알림 액션 버튼([먹었어요] [버렸어요] [하루 더])
7. 입력 자동화(우선순위 순)
   - 알림 액션 버튼 → 조리 완료 시 재료 자동 차감 → 영수증 OCR 일괄 등록 → 목록 스와이프(오른쪽=소비 완료, 왼쪽=폐기)
   - 자주 사는 품목 빠른 추가 칩, 주 1회 냉장고 점검, 사용자가 수정한 기한을 기본값으로 학습
   - OCR/자동 입력 결과는 항상 "추정" 표시 후 사용자가 확인하게 할 것

## 7. 규칙

- 한국어 UI, 코드 주석은 한국어로 짧게. 변수·클래스명은 영어.
- 화면 코드는 `FoodRepository`만 바라보게 유지. Firestore 호출을 Fragment에 직접 넣지 말 것.
- 기존 파일 구조와 패키지 구성을 유지하고, 큰 리팩터링은 먼저 제안할 것.
- 변경하면 관련 레이아웃의 `id`와 Java의 `R.id` 참조가 맞는지 확인할 것.
- 새 라이브러리를 추가하면 이유를 알려 줄 것. 버전은 현재 AGP 8.5.2 / compileSdk 34와 호환되는 것으로.
- `google-services.json`, 키스토어, API 키는 커밋하지 말 것(저장소가 비공개인지 확인 전까지).
- Git: `main` 직접 push 금지, `feature/기능명` 브랜치 → PR. 커밋 메시지는 `feat:`, `fix:`, `ui:`, `docs:` 접두사.
- 코드에 남아 있는 `TODO(팀원)` 주석은 다른 팀원 담당 영역이므로, 요청이 없으면 구현하지 말고 그대로 둘 것.
