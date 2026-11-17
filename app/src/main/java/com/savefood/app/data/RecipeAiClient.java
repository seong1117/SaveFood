package com.savefood.app.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.savefood.app.BuildConfig;
import com.savefood.app.model.Recipe;
import com.savefood.app.model.Substitution;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 레시피 AI(Groq) 클라이언트.
 *  - 요리 이름 → 레시피 초안
 *  - 부족 재료 → 보유 재료로 대체 / 빼고 조리 / 꼭 필요 판단
 * 결과는 "AI 추정"으로 표시하고, 사용자가 확인·수정한 뒤 저장한다.
 * 키는 local.properties 의 GROQ_API_KEY (커밋 금지).
 */
public class RecipeAiClient {

    public interface Callback {
        void onSuccess(Recipe draft);   // 메인 스레드에서 호출
        void onError(String message);   // 메인 스레드에서 호출 (사용자에게 보여 줄 문구)
    }

    public interface SubstituteCallback {
        void onSuccess(List<Substitution> items);  // 메인 스레드에서 호출
        void onError(String message);
    }

    private static final String TAG = "RecipeAiClient";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    // 무료 모델 목록은 Groq 콘솔에서 확인 후 필요하면 변경
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String[] DIFFICULTIES = {"쉬움", "보통", "어려움"};
    private static final String DEFAULT_EMOJI = "🍽️";

    static final String SYSTEM_PROMPT = """
            너는 1인 가구를 위한 요리 레시피 도우미야.
            사용자가 요리 이름을 주면 그 요리의 레시피를 아래 JSON 형식으로만 답해. 다른 문장은 쓰지 마.

            {
              "emoji": "요리를 나타내는 이모지 1개",
              "minutes": 총 조리 시간(정수, 분),
              "servings": 인분(정수),
              "difficulty": "쉬움" | "보통" | "어려움",
              "ingredients": [ { "name": "재료 이름", "amount": "양" } ],
              "steps": [ { "title": "짧은 단계 제목", "minutes": 정수, "description": "1~2문장 설명" } ]
            }

            규칙:
            - 모든 내용은 한국어로 써.
            - servings는 1인분 기준으로 써.
            - 재료 이름에는 수식어나 양을 넣지 마. ("신선한 우유" ✗ → "우유", "양파 1개" ✗ → name "양파", amount "1개")
            - amount는 g, ml, 개, 큰술, 작은술, 공기, 쪽, 컵 같은 한국 가정 단위로 써. 소금·후추처럼 양이 애매하면 "약간".
            - 단위는 재료에 맞게 써. (마늘 3쪽, 대파 1/2대, 양파 1/2개, 밥 1공기)
            - 재료는 3~12개, 조리 단계는 2~7개.
            - minutes는 steps의 minutes 합과 비슷하게 맞춰.
            - 조리 단계에서 쓰는 재료(기름, 양념 포함)는 모두 ingredients에 넣어.
            - 고기·생선·달걀은 안전하게 익히는 기준을 설명에 포함해. (예: 닭고기 중심 온도 75℃ 1분 이상)
            - "보유 재료"는 이름 맞추기용이야. 이 요리에 원래 들어가는 재료와 같은 것만 그 이름을 그대로 쓰고, 요리에 안 맞는 보유 재료는 절대 넣지 마.
            - 입력이 음식 이름이 아니면 {"error": "음식 이름이 아니에요"} 만 답해.
            """;

    static final String SUBSTITUTE_PROMPT = """
            너는 1인 가구를 위한 요리 도우미야.
            레시피에서 부족한 재료마다 해결 방법을 하나씩 골라 아래 JSON 형식으로만 답해. 다른 문장은 쓰지 마.

            {
              "items": [
                { "missing": "부족 재료 이름", "action": "substitute" | "omit" | "required", "use": "대체할 보유 재료 이름", "tip": "1문장 설명" }
              ]
            }

            우선순위 (위에서부터 먼저 고려해):
            1. substitute: "보유 재료" 중 맛이나 역할이 비슷해서 대신 쓸 수 있는 것이 있으면. use에 그 보유 재료 이름을 그대로 써.
            2. omit: 대체할 보유 재료는 없지만 빼도 요리가 되면. use는 "".
            3. required: 빼면 요리가 성립하지 않는 핵심 재료일 때만. use는 "", tip에 이유를 써.

            required 판단 기준:
            - 요리 이름에 들어간 주재료이거나, 그 재료 없이는 그 요리라고 부를 수 없을 때만 required. (예: 김치볶음밥의 김치·밥, 토마토 파스타의 면)
            - 채소·치즈·고명·향신 채소·양념 같은 부재료는 맛이 조금 달라져도 대부분 omit 이야.
            - 보유 재료가 하나도 없어도 같은 기준으로 판단해.

            규칙:
            - 모든 내용은 한국어로 써.
            - "부족 재료" 하나마다 정확히 1개 항목을 만들고, missing에는 부족 재료 이름을 그대로 써.
            - use에는 "보유 재료" 목록에 있는 이름만 써. 목록에 없는 재료는 절대 쓰지 마.
            - 이미 레시피에 쓰이는 보유 재료라도 양을 늘려 대체할 수 있으면 써도 돼.
            - tip은 양 조절이나 맛 차이를 짧게. (예: "우유 150ml에 버터를 조금 넣으면 크림과 비슷해요")
            """;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 키가 없으면 AI 버튼을 숨김 */
    public static boolean isAvailable() {
        return !BuildConfig.GROQ_API_KEY.trim().isEmpty();
    }

    public void generate(String dishName, List<String> ownedFoods, Callback callback) {
        String user = "요리 이름: " + dishName + "\n보유 재료: " + joinOrNone(ownedFoods);
        runAsync(() -> parseRecipe(chat(SYSTEM_PROMPT, user), dishName), callback::onSuccess, callback::onError);
    }

    /** 부족 재료 해결 방법. 반환 목록의 use 는 이름만 있고, 보유 식품 확인은 저장소에서 함 */
    public void suggestSubstitutes(Recipe recipe, List<String> missing, List<String> ownedFoods,
                                   SubstituteCallback callback) {
        List<String> ingredients = new ArrayList<>();
        for (Recipe.Ingredient ing : recipe.ingredients) ingredients.add(ing.display());
        String user = "요리: " + recipe.name
                + "\n레시피 재료: " + String.join(", ", ingredients)
                + "\n부족 재료: " + String.join(", ", missing)
                + "\n보유 재료: " + joinOrNone(ownedFoods);
        runAsync(() -> parseSubstitutions(chat(SUBSTITUTE_PROMPT, user), missing),
                callback::onSuccess, callback::onError);
    }

    private static String joinOrNone(List<String> names) {
        return names.isEmpty() ? "없음" : String.join(", ", names);
    }

    // ---------- 공통 실행 (백그라운드 → 메인 스레드) ----------
    private interface Work<T> {
        T run() throws IOException, JSONException, AiException;
    }

    private <T> void runAsync(Work<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        executor.execute(() -> {
            String error;
            try {
                T result = work.run();
                mainHandler.post(() -> onSuccess.accept(result));
                return;
            } catch (AiException e) {
                error = e.getMessage();
            } catch (IOException e) {
                Log.w(TAG, "network", e);
                error = "인터넷 연결을 확인해 주세요.";
            } catch (JSONException e) {
                Log.w(TAG, "json", e);
                error = "AI 응답을 읽지 못했어요. 다시 시도해 주세요.";
            }
            String message = error;
            mainHandler.post(() -> onError.accept(message));
        });
    }

    // ---------- 요청 ----------
    private String chat(String systemPrompt, String userContent) throws IOException, JSONException, AiException {
        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("temperature", 0.3)
                .put("response_format", new JSONObject().put("type", "json_object"))
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(new JSONObject().put("role", "user").put("content", userContent)));

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.GROQ_API_KEY.trim());
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + ": " + readAll(conn.getErrorStream()));
                if (code == 401) throw new AiException("AI 키가 올바르지 않아요. local.properties 를 확인해 주세요.");
                if (code == 429) throw new AiException("AI 요청이 많아요. 잠시 후 다시 시도해 주세요.");
                throw new AiException("AI 서버 오류가 발생했어요. (" + code + ")");
            }
            JSONObject res = new JSONObject(readAll(conn.getInputStream()));
            return res.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        }
    }

    // ---------- 응답 → 부족 재료 해결 방법 (값 검증) ----------
    static List<Substitution> parseSubstitutions(String content, List<String> missing) throws JSONException {
        JSONObject o = new JSONObject(extractJson(content));
        JSONArray arr = o.optJSONArray("items");
        List<Substitution> result = new ArrayList<>();
        // 부족 재료 순서대로, 각 재료당 1개만
        for (String name : missing) {
            Substitution found = null;
            if (arr != null) {
                for (int i = 0; i < arr.length() && found == null; i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item == null || !name.equals(str(item, "missing"))) continue;
                    String action = str(item, "action");
                    if (!Substitution.SUBSTITUTE.equals(action) && !Substitution.OMIT.equals(action)) {
                        action = Substitution.REQUIRED;
                    }
                    String use = Substitution.SUBSTITUTE.equals(action) ? str(item, "use") : "";
                    found = new Substitution(name, action, use, str(item, "tip"));
                }
            }
            if (found != null) result.add(found);
        }
        return result;
    }

    private static String extractJson(String content) throws JSONException {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) throw new JSONException("JSON 없음");
        return content.substring(start, end + 1);
    }

    // ---------- 응답 → Recipe (값 검증) ----------
    static Recipe parseRecipe(String content, String dishName) throws JSONException, AiException {
        JSONObject o = new JSONObject(extractJson(content));

        if (o.has("error")) {
            String msg = str(o, "error");
            throw new AiException(msg.isEmpty() ? "요리 이름을 다시 확인해 주세요." : msg);
        }

        Recipe r = new Recipe();
        r.name = dishName;
        String emoji = str(o, "emoji");
        r.emoji = emoji.isEmpty() ? DEFAULT_EMOJI : emoji;
        r.servings = clamp(o.optInt("servings", 1), 1, 10);
        String difficulty = str(o, "difficulty");
        r.difficulty = "보통";
        for (String d : DIFFICULTIES) if (d.equals(difficulty)) r.difficulty = d;

        JSONArray ings = o.optJSONArray("ingredients");
        if (ings != null) {
            for (int i = 0; i < ings.length(); i++) {
                JSONObject item = ings.optJSONObject(i);
                if (item == null) continue;
                String name = str(item, "name");
                if (name.isEmpty()) continue;
                r.ingredients.add(new Recipe.Ingredient(name, str(item, "amount")));
            }
        }
        if (r.ingredients.isEmpty()) throw new AiException("재료를 찾지 못했어요. 직접 입력해 주세요.");

        int stepSum = 0;
        JSONArray steps = o.optJSONArray("steps");
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                JSONObject item = steps.optJSONObject(i);
                if (item == null) continue;
                String title = str(item, "title");
                String desc = str(item, "description");
                if (title.isEmpty() && desc.isEmpty()) continue;
                int minutes = clamp(item.optInt("minutes", 0), 0, 240);
                stepSum += minutes;
                r.steps.add(new Recipe.Step(title.isEmpty() ? "단계 " + (r.steps.size() + 1) : title, minutes, desc));
            }
        }

        int minutes = o.optInt("minutes", 0);
        if (minutes <= 0) minutes = stepSum > 0 ? stepSum : 20;
        r.minutes = clamp(minutes, 1, 240);
        return r;
    }

    // null 값이면 "null" 문자열 대신 빈 문자열
    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "").trim();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 사용자에게 그대로 보여 줄 오류 */
    static class AiException extends Exception {
        AiException(String message) { super(message); }
    }
}
