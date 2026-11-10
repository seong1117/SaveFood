package com.savefood.app.ui.scan;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.savefood.app.MainActivity;
import com.savefood.app.R;
import com.savefood.app.data.FoodRepository;
import com.savefood.app.model.FoodItem;
import com.savefood.app.util.UiUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 식품 스캔/등록 화면.
 *  - 식재료: 포장 없는 식재료 사진 → FoodClassifier(팀 학습 모델) 후보 → 식품명·기한·보관 장소 채움
 *           모델이 없거나 틀리면 "자주 사는 품목" 칩(FoodCatalog)으로 바로 고른다
 *  - 가격표: 진열대 가격표 사진 → OCR → PriceTagParser(큰 글자 품명) 후보 → 식품명 채움
 *  - 영수증: 사진 → OCR → ReceiptParser(품명·수량, 구매일) → 골라서 한꺼번에 등록
 *  - 소비기한: 포장지·라벨 사진 → OCR → ExpiryDateParser(날짜 정규화) 후보 → 기한 칸 채움
 *           영수증·가격표에는 기한이 없으므로, 기한이 긴 품목은 포장지를 따로 찍어 고친다
 *  - 카메라 촬영 또는 "사진" 버튼(갤러리)으로 입력. 결과는 모두 "추정"이며 사용자가 확인한다
 *  - 카메라 세부 사항은 CameraController 가 맡는다
 */
public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";

    private enum Mode { FOOD, PRICE_TAG, RECEIPT, EXPIRY }

    private PreviewView previewView;
    private CameraController camera;
    private TextRecognizer recognizer;
    @Nullable private FoodClassifier classifier;
    private Mode mode = Mode.FOOD;

    private Button btnCapture;
    private ImageButton btnFlash;
    private TextView tvHint, tvResult;
    private ChipGroup chipsCandidates, chipsQuick;
    private View scrollQuick;
    private TextInputEditText etName, etExpiry, etQuantity;
    private ChipGroup chipsStatus;
    private MaterialButtonToggleGroup toggleLocation;

    private LocalDate expiry = LocalDate.now().plusDays(7);
    /** 기한을 포장지에서 읽었거나 직접 골랐으면, 품목을 바꿔도 기본 일수로 덮어쓰지 않는다 */
    private boolean expiryFixed = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showNoPermission();
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) recognizeFile(uri);
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
        btnCapture = view.findViewById(R.id.btn_capture);
        btnFlash = view.findViewById(R.id.btn_flash);
        tvHint = view.findViewById(R.id.tv_camera_hint);
        tvResult = view.findViewById(R.id.tv_scan_result);
        chipsCandidates = view.findViewById(R.id.chips_candidates);
        chipsQuick = view.findViewById(R.id.chips_quick);
        scrollQuick = view.findViewById(R.id.scroll_quick);
        etName = view.findViewById(R.id.et_name);
        etExpiry = view.findViewById(R.id.et_expiry);
        etQuantity = view.findViewById(R.id.et_quantity);
        chipsStatus = view.findViewById(R.id.chips_status);
        toggleLocation = view.findViewById(R.id.toggle_location);

        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        if (FoodClassifier.isAvailable(requireContext())) {
            try {
                classifier = new FoodClassifier(requireContext());
            } catch (Exception e) {
                Log.e(TAG, "식재료 모델을 불러오지 못함", e);
            }
        }
        camera = new CameraController(requireContext(), getViewLifecycleOwner(), previewView);

        setupQuickChips();
        updateExpiryText();
        etExpiry.setOnClickListener(v -> showDatePicker());
        btnCapture.setOnClickListener(v -> onCaptureClicked());
        btnFlash.setOnClickListener(v -> btnFlash.setSelected(camera.toggleTorch()));
        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> pickPhoto.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        view.findViewById(R.id.btn_register).setOnClickListener(v -> register());

        MaterialButtonToggleGroup toggleMode = view.findViewById(R.id.toggle_mode);
        toggleMode.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            if (id == R.id.btn_mode_receipt) setMode(Mode.RECEIPT);
            else if (id == R.id.btn_mode_expiry) setMode(Mode.EXPIRY);
            else if (id == R.id.btn_mode_price) setMode(Mode.PRICE_TAG);
            else setMode(Mode.FOOD);
        });
        setMode(Mode.FOOD);

        if (hasCameraPermission()) startCamera();
        else permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 설정 화면에서 권한을 켜고 돌아온 경우
        if (camera != null && !camera.isStarted() && hasCameraPermission()) startCamera();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recognizer != null) recognizer.close();
        if (classifier != null) classifier.close();
        classifier = null;
        if (camera != null) camera.close();
        camera = null;
    }

    // ---------------- 카메라 ----------------
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        camera.start(new CameraController.StartListener() {
            @Override
            public void onStarted(boolean hasFlash) {
                btnFlash.setVisibility(hasFlash ? View.VISIBLE : View.GONE);
                setMode(mode);   // 안내 문구 다시 적용
            }

            @Override
            public void onError(Exception e) {
                toast("카메라를 시작할 수 없어요");
            }
        });
    }

    private void showNoPermission() {
        tvHint.setText("카메라 권한이 필요해요 (사진 불러오기·직접 입력은 가능)");
        btnCapture.setText("카메라 권한 허용");
    }

    private void setMode(Mode newMode) {
        mode = newMode;
        tvResult.setVisibility(View.GONE);
        chipsCandidates.setVisibility(View.GONE);
        scrollQuick.setVisibility(mode == Mode.FOOD ? View.VISIBLE : View.GONE);
        if (camera == null) return;
        if (!hasCameraPermission()) {
            showNoPermission();
            return;
        }
        if (mode == Mode.RECEIPT) {
            btnCapture.setText("🧾 영수증 촬영");
            tvHint.setText("영수증 전체가 보이게 맞춰 주세요");
        } else if (mode == Mode.EXPIRY) {
            btnCapture.setText("📅 소비기한 촬영");
            tvHint.setText("포장지의 날짜 부분을 가까이 찍어 주세요");
        } else if (mode == Mode.PRICE_TAG) {
            btnCapture.setText("🏷 가격표 촬영");
            tvHint.setText("가격표 하나가 화면에 꽉 차게 찍어 주세요");
        } else {
            btnCapture.setText("📷 식재료 촬영");
            tvHint.setText(classifier != null ? "식재료 하나를 가운데에 놓아 주세요"
                    : "사진 인식 준비 중 · 아래 품목을 눌러 주세요");
        }
    }

    private void onCaptureClicked() {
        if (!hasCameraPermission()) requestPermissionAgain();
        else capture();
    }

    /** 한 번 거부했으면 다시 묻고, "다시 묻지 않음"이면 앱 설정으로 보낸다. */
    private void requestPermissionAgain() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null));
            startActivity(intent);
            toast("권한 → 카메라를 허용해 주세요");
        }
    }

    private void capture() {
        boolean started = camera.takePicture(new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                // 미리보기에 보이던 부분만 읽는다(옆 가격표 글자가 섞이지 않게)
                InputImage input = CameraController.toInputImage(image);
                if (input == null) {
                    image.close();
                    return;
                }
                recognize(input, image::close);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "촬영 실패", exception);
                toast("촬영에 실패했어요");
            }
        });
        if (!started) toast("카메라가 준비되지 않았어요 (권한을 확인하세요)");
        else btnCapture.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void recognizeFile(@NonNull Uri uri) {
        try {
            recognize(InputImage.fromFilePath(requireContext(), uri), () -> { });
        } catch (Exception e) {
            Log.e(TAG, "사진을 열 수 없음", e);
            toast("사진을 열 수 없어요");
        }
    }

    /** 현재 모드에 맞게 인식한다. 끝나면 done 을 호출(촬영 이미지 닫기). */
    private void recognize(@NonNull InputImage image, @NonNull Runnable done) {
        if (mode != Mode.FOOD) {
            Mode shotMode = mode;
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        if (shotMode == Mode.RECEIPT) applyReceipt(text);
                        else if (shotMode == Mode.EXPIRY) applyExpiry(text);
                        else applyPriceTag(text);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR 실패", e);
                        toast("글자를 인식하지 못했어요");
                    })
                    .addOnCompleteListener(t -> done.run());
        } else if (classifier != null) {
            classifier.classify(image)
                    .addOnSuccessListener(this::showCandidates)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "분류 실패", e);
                        toast("식재료를 알아보지 못했어요");
                    })
                    .addOnCompleteListener(t -> done.run());
        } else {
            done.run();
            toast("사진 인식은 준비 중이에요. 아래 품목 칩에서 골라 주세요");
        }
    }

    // ---------------- 식재료 ----------------
    private void setupQuickChips() {
        for (FoodCatalog.Entry e : FoodCatalog.all()) {
            Chip chip = new Chip(requireContext());
            chip.setText(e.emoji + " " + e.name);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> applyFood(e.name));
            chipsQuick.addView(chip);
        }
    }

    private void showCandidates(List<FoodClassifier.Guess> guesses) {
        if (!isAdded()) return;
        List<String> names = new ArrayList<>(), labels = new ArrayList<>();
        for (FoodClassifier.Guess g : guesses) {
            names.add(g.name);
            labels.add(String.format(Locale.KOREA, "%s %s %.0f%%",
                    FoodCatalog.emojiFor(g.name), g.name, g.confidence * 100));
        }
        showChoices(names, labels, "식재료를 알아보지 못했어요. 직접 입력해 주세요");
    }

    /** 후보를 칩으로 보여 주고 첫 번째를 바로 채운다. 칩을 누르면 그 후보로 바꾼다. */
    private void showChoices(List<String> names, List<String> labels, String emptyMessage) {
        chipsCandidates.removeAllViews();
        if (names.isEmpty()) {
            chipsCandidates.setVisibility(View.GONE);
            toast(emptyMessage);
            return;
        }
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Chip chip = new Chip(requireContext());
            chip.setText(labels.get(i));
            chip.setCheckable(true);
            chip.setOnClickListener(v -> applyFood(name));
            chipsCandidates.addView(chip);
        }
        chipsCandidates.setVisibility(View.VISIBLE);
        ((Chip) chipsCandidates.getChildAt(0)).setChecked(true);
        applyFood(names.get(0));
        toast("추정 결과예요. 맞는 품목을 골라 주세요");
    }

    /** 품목 이름으로 식품명·소비기한·보관 장소를 채운다. 기한을 이미 읽었으면 기한은 그대로 둔다. */
    private void applyFood(String name) {
        etName.setText(name);
        if (!expiryFixed) {
            expiry = LocalDate.now().plusDays(FoodCatalog.daysFor(name));
            updateExpiryText();
        }
        FoodCatalog.Entry e = FoodCatalog.find(name);
        if (e != null) toggleLocation.check(locationButton(e.location));
    }

    // ---------------- 가격표 ----------------
    private void applyPriceTag(Text text) {
        if (!isAdded()) return;
        Log.d(TAG, "가격표 OCR 원문:\n" + text.getText());   // 파서 보정용 샘플
        List<String> names = PriceTagParser.names(toPieces(text));
        List<String> labels = new ArrayList<>();
        for (String n : names) labels.add(FoodCatalog.emojiFor(n) + " " + n);
        showChoices(names, labels, "품명을 찾지 못했어요. 가격표를 가까이서 다시 찍어 주세요");
    }

    /** ML Kit 결과를 줄 단위 조각(글자 + 위치)으로 바꾼다. */
    private static List<OcrPiece> toPieces(Text text) {
        List<OcrPiece> pieces = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect b = line.getBoundingBox();
                if (b != null) pieces.add(new OcrPiece(line.getText(), b.left, b.top, b.right, b.bottom));
            }
        }
        return pieces;
    }

    // ---------------- 소비기한 ----------------
    private void applyExpiry(Text text) {
        if (!isAdded()) return;
        Log.d(TAG, "소비기한 OCR 원문:\n" + text.getText());   // 파서 보정용 샘플
        List<ExpiryDateParser.Candidate> found = ExpiryDateParser.candidates(toPieces(text), LocalDate.now());
        chipsCandidates.removeAllViews();
        if (found.isEmpty()) {
            chipsCandidates.setVisibility(View.GONE);
            tvResult.setVisibility(View.GONE);
            toast("날짜를 찾지 못했어요. 날짜 부분을 가까이서 다시 찍어 주세요");
            return;
        }
        for (ExpiryDateParser.Candidate c : found) {
            Chip chip = new Chip(requireContext());
            chip.setText("📅 " + UiUtils.formatDate(c.date) + " · " + c.note);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> applyExpiryDate(c));
            chipsCandidates.addView(chip);
        }
        chipsCandidates.setVisibility(View.VISIBLE);
        ((Chip) chipsCandidates.getChildAt(0)).setChecked(true);
        applyExpiryDate(found.get(0));
        toast("추정 결과예요. 맞는 날짜를 골라 주세요");
    }

    private void applyExpiryDate(ExpiryDateParser.Candidate c) {
        expiry = c.date;
        expiryFixed = true;
        updateExpiryText();
        String note = c.kind == ExpiryDateParser.Kind.UNLABELED ? "날짜만 보여 기한으로 추정" : c.note;
        showResult("소비기한 (추정): " + ExpiryDateParser.toIso(c.date) + "\n근거: " + note);
    }

    // ---------------- 영수증 ----------------
    private void applyReceipt(Text text) {
        if (!isAdded()) return;
        Log.d(TAG, "영수증 OCR 원문:\n" + text.getText());   // 파서 보정용 샘플
        ReceiptParser.Result result = ReceiptParser.parse(toPieces(text));
        String dateText = result.date == null ? "찾지 못함 (오늘로 등록)" : UiUtils.formatDate(result.date);
        showResult("구매일 (추정): " + dateText + "\n품목 " + result.items.size() + "개 (추정)");
        if (result.items.isEmpty()) {
            toast("품목을 찾지 못했어요. 영수증이 잘 보이게 다시 찍어 주세요");
            return;
        }
        showReceiptDialog(result);
    }

    private void showReceiptDialog(ReceiptParser.Result result) {
        List<ReceiptParser.Item> items = result.items;
        String[] labels = new String[items.size()];
        boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = FoodCatalog.emojiFor(items.get(i).name) + " " + items.get(i);
            checked[i] = true;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("영수증에서 찾은 품목 (추정)")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("등록", (d, w) -> {
                    LocalDate bought = result.date != null ? result.date : LocalDate.now();
                    int count = 0;
                    for (int i = 0; i < items.size(); i++) {
                        if (!checked[i]) continue;
                        addFood(items.get(i).name, items.get(i).quantity, bought, null, null);
                        count++;
                    }
                    toast(count + "개 품목을 냉장고에 등록했어요");
                    if (count > 0) ((MainActivity) requireActivity()).switchTab(R.id.nav_home);
                })
                .show();
    }

    private void showResult(String text) {
        tvResult.setText(text);
        tvResult.setVisibility(View.VISIBLE);
    }

    // ---------------- 입력/등록 ----------------
    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            expiry = LocalDate.of(y, m + 1, d);
            expiryFixed = true;
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

        addFood(name, quantity, LocalDate.now(), expiry, locationText());
        expiryFixed = false;
        toast("냉장고에 등록했어요");
        ((MainActivity) requireActivity()).switchTab(R.id.nav_home);
    }

    /** expiry·location 이 null 이면 품목표 기본값(없으면 7일, 냉장실)을 쓴다. */
    private void addFood(String name, int quantity, LocalDate registered,
                         @Nullable LocalDate expiryDate, @Nullable String location) {
        FoodCatalog.Entry e = FoodCatalog.find(name);
        if (expiryDate == null) expiryDate = registered.plusDays(FoodCatalog.daysFor(name));
        if (location == null) location = e != null ? e.location : "냉장실";
        FoodItem item = new FoodItem(UUID.randomUUID().toString(), name, FoodCatalog.emojiFor(name),
                location, quantity, registered, expiryDate);
        item.status = statusText();
        FoodRepository.get().addFood(item);   // TODO(팀원): Firestore 저장
    }

    private int locationButton(String location) {
        if ("냉동실".equals(location)) return R.id.btn_loc_freezer;
        if ("실온".equals(location)) return R.id.btn_loc_room;
        return R.id.btn_loc_fridge;
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
