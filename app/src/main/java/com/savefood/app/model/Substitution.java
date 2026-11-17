package com.savefood.app.model;

/**
 * 부족 재료 해결 방법 (AI 추정).
 * 우선순위: 보유 재료로 대체 → 빼고 조리 → 꼭 필요(구매)
 */
public class Substitution {
    public static final String SUBSTITUTE = "substitute";  // 보유 재료로 대체
    public static final String OMIT = "omit";              // 빼고 조리 가능
    public static final String REQUIRED = "required";      // 꼭 필요한 재료

    public String missing;     // 부족 재료 이름 (레시피 그대로)
    public String action;      // SUBSTITUTE / OMIT / REQUIRED
    public String use;         // 대체할 보유 재료 이름 (SUBSTITUTE 일 때만)
    public String useFoodId;   // 대체할 보유 식품 id (저장소에서 확인한 값)
    public String tip;         // 양 조절·맛 차이 등 짧은 설명

    public Substitution() { }

    public Substitution(String missing, String action, String use, String tip) {
        this.missing = missing;
        this.action = action;
        this.use = use;
        this.tip = tip;
    }

    public boolean isSubstitute() {
        return SUBSTITUTE.equals(action) && useFoodId != null;
    }
}
