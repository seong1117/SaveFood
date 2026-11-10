package com.savefood.app.data;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * 사용자 프로필(users/{uid}) 저장소.
 *
 * 구조:  users/{uid}  { email, nickname, createdAt, termsAgreedAt, updatedAt }
 *        users/{uid}/foods/{id}, users/{uid}/leftovers/{id}   ← 식품 데이터(추가 예정)
 *
 * 회원 탈퇴 시 하위 컬렉션까지 지우므로, 하위 컬렉션을 새로 만들면
 * {@link #deleteUserData} 의 목록에도 추가해야 한다.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";
    private static final String[] SUB_COLLECTIONS = {"foods", "leftovers"};

    public interface Callback {
        /** error 가 null 이면 성공 */
        void onComplete(@Nullable Exception error);
    }

    private static UserRepository instance;

    public static synchronized UserRepository get() {
        if (instance == null) instance = new UserRepository();
        return instance;
    }

    private UserRepository() { }

    private FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    /** 회원가입 직후 프로필 문서 생성 */
    public void saveNewUser(FirebaseUser user, String nickname) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("email", user.getEmail());
        doc.put("nickname", nickname);
        doc.put("createdAt", FieldValue.serverTimestamp());
        doc.put("termsAgreedAt", FieldValue.serverTimestamp());
        db().collection("users").document(user.getUid())
                .set(doc, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "프로필 문서 생성 실패 (Firestore 규칙 확인)", e));
    }

    public void updateNickname(String uid, String nickname) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("nickname", nickname);
        doc.put("updatedAt", FieldValue.serverTimestamp());
        db().collection("users").document(uid)
                .set(doc, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "닉네임 저장 실패", e));
    }

    /** 사용자 문서와 하위 컬렉션 전체 삭제 (탈퇴 시 사용) */
    public void deleteUserData(String uid, Callback callback) {
        FirebaseFirestore db = db();
        DocumentReference userDoc = db.collection("users").document(uid);

        Task<?>[] tasks = new Task<?>[SUB_COLLECTIONS.length];
        for (int i = 0; i < SUB_COLLECTIONS.length; i++) {
            tasks[i] = deleteCollection(db, userDoc.collection(SUB_COLLECTIONS[i]));
        }

        Tasks.whenAll(tasks)
                .continueWithTask(t -> {
                    t.getResult();              // 앞 단계가 실패했다면 여기서 예외 발생
                    return userDoc.delete();
                })
                .addOnCompleteListener(t -> callback.onComplete(t.isSuccessful() ? null : t.getException()));
    }

    /** 컬렉션의 문서를 삭제한다. (한 번에 최대 500개 — 데이터가 많아지면 반복 호출 필요) */
    private static Task<Void> deleteCollection(FirebaseFirestore db, CollectionReference col) {
        return col.get().continueWithTask(t -> {
            QuerySnapshot snap = t.getResult();
            WriteBatch batch = db.batch();
            int count = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                batch.delete(d.getReference());
                if (++count >= 500) break;
            }
            if (count == 0) {
                return Tasks.forResult((Void) null);
            }
            return batch.commit();
        });
    }
}
