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
├─ ui/scan/   ScanFragment (CameraX + ML Kit OCR + 등록 폼)
├─ ui/recipe/ RecipeFragment, RecipeDetailActivity
├─ ui/settings/ SettingsFragment (계정·로그아웃)
├─ util/      UiUtils
└─ MainActivity (하단 탭 4개)
```

화면 흐름: `LoginActivity → MainActivity(홈/스캔/레시피/설정)`
홈에서 식품·남은 요리 클릭 → 바텀시트 메뉴(보관 기한 추적, 상태 정보, 재조리 가이드)

## 3. 구현 상태 / 팀원 TODO

코드 안의 `TODO(팀원)` 주석으로 검색할 수 있습니다.

| 영역                     | 상태                  | 해야 할 일                                                               |
| ------------------------ | --------------------- | ------------------------------------------------------------------------ |
| 로그인/회원가입/로그아웃 | 구현                  | 비밀번호 변경, 소셜 로그인(선택)                                         |
| 홈 · 식품/남은 요리 메뉴 | UI 완료 (더미 데이터) | 수량·기한 수정 다이얼로그, 소비/폐기 이력 저장                           |
| 데이터 저장              | **미구현**            | `FoodRepository` → Firestore (`users/{uid}/foods`, `leftovers`)          |
| 스캔 · OCR               | 기본 동작             | 인식 정확도 개선, 바코드, 영수증 일괄 등록, 권장 기한 자동 설정          |
| 레시피 추천              | 구현 완료 (보유·임박 재료 기반 + 필터 칩) | —                                                        |
| 조리 완료                | **미구현**            | 재료 차감 → 남은 음식 등록(보관 장소·기한·알림)                          |
| 알림                     | **미구현**            | 소비기한 임박 푸시/로컬 알림 + 알림 액션 버튼(먹었어요/버렸어요/하루 더) |
| 남은 음식 전체 목록      | **미구현**            | 홈 "전체보기" 화면, 이번 달 절약 통계                                    |

## 4. Git 규칙 (제안)

- `main`: 항상 빌드되는 상태 / 직접 push 금지
- 기능 브랜치: `feature/기능명` (예: `feature/firestore-food`) → Pull Request → 1명 이상 리뷰 후 merge
- 커밋 메시지: `feat: …`, `fix: …`, `ui: …`, `docs: …`
- 같은 파일(특히 `res/layout`, `FoodRepository`)을 동시에 수정하지 않도록 담당 영역을 나눠 작업
