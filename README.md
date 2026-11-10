# 세이프 푸드 (SaveFood)

식재료 소비기한을 추적하고, 임박 재료로 맞춤 레시피를 추천하는 안드로이드 앱 (팀 프로젝트 프로토타입).

- 언어/IDE: Java 17, Android Studio (Koala 이상 권장)
- minSdk 26 / targetSdk 34
- Firebase Authentication + Cloud Firestore, CameraX, ML Kit(한국어 OCR)

## 1. 실행 방법

1. Android Studio → **Open** → 이 폴더 선택 → Gradle Sync
2. 에뮬레이터 또는 실기기에서 Run
3. **Firebase 설정 전**에는 로그인 화면의 `로그인 없이 둘러보기`로 모든 화면을 확인할 수 있습니다.

### Firebase 연결 (계정 담당자가 1회 진행)
1. [Firebase Console](https://console.firebase.google.com) → 프로젝트 생성 → Android 앱 추가 (패키지명 `com.savefood.app`)
2. `google-services.json` 을 **`app/`** 폴더에 넣기 (파일이 있을 때만 플러그인이 자동 적용됨)
3. Authentication → 로그인 방법 → **이메일/비밀번호** 사용 설정
4. Firestore Database 생성 (개발 중에는 테스트 모드, 이후 보안 규칙 설정)
5. 팀원 개발용 디버그 키 등록이 필요하면 `./gradlew signingReport` 의 SHA-1 을 Firebase 앱 설정에 추가

> `google-services.json` 은 비공개(Private) 저장소에 올리거나, 올리지 않고 팀 채널로 공유하세요. 올리지 않으려면 `.gitignore` 에 `app/google-services.json` 을 추가합니다.

## 2. 프로젝트 구조

```
app/src/main/java/com/savefood/app/
├─ auth/      LoginActivity(로그인·회원가입·게스트), AuthHelper
├─ model/     FoodItem, Leftover, Recipe
├─ data/      FoodRepository  ← 지금은 더미 데이터. Firestore 로 교체할 곳
├─ ui/home/   HomeFragment, FoodMenuBottomSheet, LeftoverBottomSheet, 어댑터
├─ ui/scan/   ScanFragment(식재료 사진·가격표·영수증·소비기한 + 등록 폼), CameraController(CameraX),
│             FoodClassifier(식재료 분류), PriceTagParser(가격표 품명), ReceiptParser(영수증 파싱), ExpiryDateParser(포장지 기한 → YYYY-MM-DD), FoodCatalog(품목 기본값)
├─ ui/recipe/ RecipeFragment, RecipeDetailActivity
├─ ui/settings/ SettingsFragment (계정·로그아웃)
├─ util/      UiUtils
└─ MainActivity (하단 탭 4개)
```

화면 흐름: `LoginActivity → MainActivity(홈/스캔/레시피/설정)`
홈에서 식품·남은 요리 클릭 → 바텀시트 메뉴(보관 기한 추적, 상태 정보, 재조리 가이드)

## 3. 구현 상태 / 팀원 TODO

코드 안의 `TODO(팀원)` 주석으로 검색할 수 있습니다.

| 영역 | 상태 | 해야 할 일 |
|---|---|---|
| 로그인/회원가입/로그아웃 | 구현 | 비밀번호 변경, 소셜 로그인(선택) |
| 홈 · 식품/남은 요리 메뉴 | UI 완료 (더미 데이터) | 수량·기한 수정 다이얼로그, 소비/폐기 이력 저장 |
| 데이터 저장 | **미구현** | `FoodRepository` → Firestore (`users/{uid}/foods`, `leftovers`) |
| 스캔 · OCR | 기본 동작 | 포장 없는 식재료 사진 등록, 가격표 품명 인식, 영수증 파싱(품명·수량) 일괄 등록, 포장지 소비기한 인식(날짜 정규화), 권장 기한 자동 설정 |
| 레시피 추천 | UI 완료 (고정 4개) | 보유/임박 재료 기반 추천 로직, 필터 칩 |
| 조리 완료 | **미구현** | 재료 차감 → 남은 음식 등록(보관 장소·기한·알림) |
| 알림 | **미구현** | 소비기한 임박 푸시/로컬 알림 + 알림 액션 버튼(먹었어요/버렸어요/하루 더) |
| 남은 음식 전체 목록 | **미구현** | 홈 "전체보기" 화면, 이번 달 절약 통계 |

## 4. Git 규칙 (제안)

- `main`: 항상 빌드되는 상태 / 직접 push 금지
- 기능 브랜치: `feature/기능명` (예: `feature/firestore-food`) → Pull Request → 1명 이상 리뷰 후 merge
- 커밋 메시지: `feat: …`, `fix: …`, `ui: …`, `docs: …`
- 같은 파일(특히 `res/layout`, `FoodRepository`)을 동시에 수정하지 않도록 담당 영역을 나눠 작업

## 5. 계정 기능

| 기능 | 위치 |
|---|---|
| 로그인 / 자동 로그인 / 게스트 둘러보기 | `auth/LoginActivity` |
| 회원가입(닉네임·약관 동의·인증 메일 발송) | `auth/SignUpActivity` |
| 비밀번호 찾기(재설정 메일) | `LoginActivity` 의 다이얼로그 |
| 계정 관리(닉네임·비밀번호 변경, 이메일 인증, 로그아웃, 회원 탈퇴) | `ui/settings/AccountActivity` |
| 사용자 프로필/데이터 삭제 | `data/UserRepository` (`users/{uid}`) |
| 오류 메시지 한글화 / 입력 검증 | `auth/AuthErrors`, `auth/AuthValidator` |

Firebase 콘솔에서 한 번 설정할 것:
1. Authentication → 로그인 방법 → 이메일/비밀번호 사용 설정
2. Firestore → 규칙 탭에 `firestore.rules` 내용을 게시
3. (선택) Authentication → 템플릿 → 이메일 주소 확인 / 비밀번호 재설정 → 언어를 한국어로 변경

비밀번호 정책(8자 이상, 영문+숫자)은 `AuthValidator.password()` 한 곳에서 바꿀 수 있습니다.
회원 탈퇴 시 `users/{uid}/foods`, `leftovers` 하위 컬렉션을 함께 지우므로, 하위 컬렉션을 추가하면 `UserRepository.SUB_COLLECTIONS` 에도 넣어 주세요.
