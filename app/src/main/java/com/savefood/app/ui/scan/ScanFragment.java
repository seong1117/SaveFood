package com.savefood.app.ui.scan;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.savefood.app.MainActivity;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.FoodItem;
import com.savefood.app.util.UiUtils;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 식품 스캔/등록 화면.
 *  - CameraX 로 미리보기, 촬영 시 ML Kit(한국어 OCR)로 글자를 읽어 식품명·소비기한을 자동 입력
 *  - 입력 확인 후 "냉장고에 등록"
 *
 * TODO(팀원):
 *  - OCR 결과에서 제품명/소비기한 추출 정확도 개선 (현재는 첫 줄 + 날짜 정규식)
 *  - 바코드 스캔(ML Kit barcode-scanning) + 식품안전나라 API 연동
 *  - 영수증 촬영 → 여러 품목 일괄 등록
 *  - 식품 종류별 권장 소비기한 자동 설정
 */
public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(20\\d{2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private TextRecognizer recognizer;

    private TextInputEditText etName, etExpiry, etQuantity;
    private ChipGroup chipsStatus;
    private MaterialButtonToggleGroup toggleLocation;

    private LocalDate expiry = LocalDate.now().plusDays(7);

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else toast("카메라 권한이 없어 직접 입력만 가능해요");
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.preview);
        etName = view.findViewById(R.id.et_name);
        etExpiry = view.findViewById(R.id.et_expiry);
        etQuantity = view.findViewById(R.id.et_quantity);
        chipsStatus = view.findViewById(R.id.chips_status);
        toggleLocation = view.findViewById(R.id.toggle_location);

        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

        updateExpiryText();
        etExpiry.setOnClickListener(v -> showDatePicker());
        view.findViewById(R.id.btn_capture).setOnClickListener(v -> captureAndRecognize());
        view.findViewById(R.id.btn_register).setOnClickListener(v -> register());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recognizer != null) recognizer.close();
    }

    // ---------------- 카메라 ----------------
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                provider.unbindAll();
                provider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "카메라 시작 실패", e);
                toast("카메라를 시작할 수 없어요");
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    // ---------------- OCR ----------------
    private void captureAndRecognize() {
        if (imageCapture == null) {
            toast("카메라가 준비되지 않았어요 (권한을 확인하세요)");
            return;
        }
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    @androidx.annotation.OptIn(markerClass = ExperimentalGetImage.class)
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        android.media.Image media = image.getImage();
                        if (media == null) {
                            image.close();
                            return;
                        }
                        InputImage input = InputImage.fromMediaImage(media, image.getImageInfo().getRotationDegrees());
                        recognizer.process(input)
                                .addOnSuccessListener(result -> {
                                    image.close();
                                    applyOcrText(result.getText());
                                })
                                .addOnFailureListener(e -> {
                                    image.close();
                                    Log.e(TAG, "OCR 실패", e);
                                    toast("글자를 인식하지 못했어요");
                                });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "촬영 실패", exception);
                        toast("촬영에 실패했어요");
                    }
                });
    }

    /** OCR 원문에서 식품명(첫 줄)과 소비기한(날짜 패턴)을 뽑아 입력창에 채운다. */
    private void applyOcrText(String text) {
        if (text == null || text.trim().isEmpty()) {
            toast("인식된 글자가 없어요");
            return;
        }
        for (String line : text.split("\n")) {
            if (!line.trim().isEmpty()) {
                etName.setText(line.trim());
                break;
            }
        }
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                expiry = LocalDate.of(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                updateExpiryText();
            } catch (Exception ignored) {
                // 잘못된 날짜 문자열이면 무시
            }
        }
        toast("인식 결과를 확인하고 수정하세요");
    }

    // ---------------- 입력/등록 ----------------
    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            expiry = LocalDate.of(y, m + 1, d);
            updateExpiryText();
        }, expiry.getYear(), expiry.getMonthValue() - 1, expiry.getDayOfMonth()).show();
    }

    private void updateExpiryText() {
        etExpiry.setText(UiUtils.formatDate(expiry));
    }

    private void register() {
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("식품명을 입력하세요");
            return;
        }
        int quantity = 1;
        try {
            quantity = Integer.parseInt(String.valueOf(etQuantity.getText()));
        } catch (NumberFormatException ignored) { }

        FoodItem item = new FoodItem(UUID.randomUUID().toString(), name, "🛒", locationText(), quantity,
                LocalDate.now(), expiry);
        item.status = statusText();
        FoodRepository.get().addFood(item);   // TODO(팀원): Firestore 저장

        toast("냉장고에 등록했어요");
        ((MainActivity) requireActivity()).switchTab(R.id.nav_home);
    }

    private String locationText() {
        int id = toggleLocation.getCheckedButtonId();
        if (id == R.id.btn_loc_freezer) return "냉동실";
        if (id == R.id.btn_loc_room) return "실온";
        return "냉장실";
    }

    private String statusText() {
        int id = chipsStatus.getCheckedChipId();
        if (id == R.id.chip_aging) return "숙성 중";
        if (id == R.id.chip_warn) return "주의";
        return "양호";
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
