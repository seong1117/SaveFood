package com.savefood.app.ui.scan;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 포장 없는 식재료 사진 분류(ML Kit 커스텀 이미지 라벨링).
 * 모델은 팀이 Teachable Machine 으로 학습해 assets 에 넣는다:
 *   assets/food_model.tflite  (TensorFlow Lite 내보내기)
 *   assets/food_labels.txt    (한 줄에 하나, "0 사과" 형식도 됨)
 * 모델 파일이 없으면 isAvailable() 이 false 이고, 화면은 직접 입력으로 안내한다.
 */
public class FoodClassifier {

    public static final class Guess {
        public final String name;
        public final float confidence;

        Guess(String name, float confidence) {
            this.name = name;
            this.confidence = confidence;
        }
    }

    private static final String TAG = "FoodClassifier";
    static final String MODEL = "food_model.tflite";
    static final String LABELS = "food_labels.txt";

    private final ImageLabeler labeler;
    private final List<String> labels;

    public static boolean isAvailable(@NonNull Context context) {
        try {
            List<String> files = Arrays.asList(context.getAssets().list(""));
            return files.contains(MODEL) && files.contains(LABELS);
        } catch (IOException e) {
            return false;
        }
    }

    public FoodClassifier(@NonNull Context context) throws IOException {
        labels = readLabels(context);
        LocalModel model = new LocalModel.Builder().setAssetFilePath(MODEL).build();
        labeler = ImageLabeling.getClient(new CustomImageLabelerOptions.Builder(model)
                .setConfidenceThreshold(0.2f)
                .setMaxResultCount(3)
                .build());
    }

    /** 확률이 높은 순서로 최대 3개. */
    @NonNull
    public Task<List<Guess>> classify(@NonNull InputImage image) {
        return labeler.process(image).continueWith(task -> {
            List<Guess> out = new ArrayList<>();
            for (ImageLabel l : task.getResult()) {
                out.add(new Guess(nameOf(l), l.getConfidence()));
            }
            return out;
        });
    }

    public void close() {
        labeler.close();
    }

    // Teachable Machine 모델에는 라벨 메타데이터가 없어 index 로 이름을 찾는다
    private String nameOf(ImageLabel l) {
        if (!l.getText().isEmpty()) return l.getText();
        int i = l.getIndex();
        return i >= 0 && i < labels.size() ? labels.get(i) : "알 수 없음";
    }

    private static List<String> readLabels(Context context) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                context.getAssets().open(LABELS), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim().replaceFirst("^\\d+\\s+", "");   // "0 사과" → "사과"
                if (!line.isEmpty()) out.add(line);
            }
        }
        Log.d(TAG, "라벨 " + out.size() + "개: " + out);
        return out;
    }
}
