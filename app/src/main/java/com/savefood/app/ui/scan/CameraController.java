package com.savefood.app.ui.scan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;

import java.nio.ByteBuffer;

/**
 * CameraX 연결을 맡는다: 미리보기, 촬영, 탭 초점, 플래시, 미리보기 영역만 잘라 인식.
 * 화면 로직은 ScanFragment 에 두고, 카메라 세부 사항은 여기서만 다룬다.
 */
public class CameraController {

    public interface StartListener {
        void onStarted(boolean hasFlash);
        void onError(Exception e);
    }

    private static final String TAG = "CameraController";

    private final Context context;
    private final LifecycleOwner owner;
    private final PreviewView previewView;

    @Nullable private Camera camera;
    @Nullable private ImageCapture imageCapture;
    private boolean torchOn = false;
    private boolean starting = false;

    public CameraController(@NonNull Context context, @NonNull LifecycleOwner owner,
                            @NonNull PreviewView previewView) {
        this.context = context;
        this.owner = owner;
        this.previewView = previewView;
    }

    public boolean isStarted() {
        return camera != null || starting;
    }

    /** 후면 카메라에 미리보기·촬영을 연결한다. */
    public void start(@NonNull StartListener listener) {
        if (isStarted()) return;
        starting = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            starting = false;
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                // 미리보기는 센서 화면의 가운데만 보여 준다. 같은 영역을 촬영 이미지의 cropRect 로 받는다
                ViewPort viewPort = previewView.getViewPort();
                if (viewPort != null) {
                    UseCaseGroup group = new UseCaseGroup.Builder()
                            .setViewPort(viewPort)
                            .addUseCase(preview)
                            .addUseCase(imageCapture)
                            .build();
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group);
                } else {
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageCapture);
                }
                setupTapToFocus();
                listener.onStarted(camera.getCameraInfo().hasFlashUnit());
            } catch (Exception e) {
                Log.e(TAG, "카메라 시작 실패", e);
                listener.onError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /** 한 장 찍어 콜백으로 넘긴다. 콜백에서 image.close() 를 꼭 호출할 것. */
    public boolean takePicture(@NonNull ImageCapture.OnImageCapturedCallback callback) {
        if (imageCapture == null) return false;
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), callback);
        return true;
    }

    /**
     * 촬영 이미지 → ML Kit 입력. 미리보기에 보이던 영역(cropRect)만 잘라서 넘긴다.
     * 화면 밖 글자(옆 가격표, 다른 상품)가 섞이지 않게 하려는 것. 자르기에 실패하면 전체 이미지를 쓴다.
     * 돌려준 뒤에도 image.close() 는 호출한 쪽에서 한다.
     */
    @Nullable
    @OptIn(markerClass = ExperimentalGetImage.class)
    public static InputImage toInputImage(@NonNull ImageProxy image) {
        int rotation = image.getImageInfo().getRotationDegrees();
        Rect crop = image.getCropRect();
        boolean cropped = crop.width() < image.getWidth() || crop.height() < image.getHeight();
        if (cropped && image.getFormat() == ImageFormat.JPEG) {
            try {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                buffer.rewind();
                byte[] jpeg = new byte[buffer.remaining()];
                buffer.get(jpeg);
                // 필요한 영역만 디코딩한다(전체를 Bitmap 으로 만들지 않아 메모리를 아낀다)
                @SuppressWarnings("deprecation")
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.length, false);
                if (decoder != null) {
                    Bitmap part = decoder.decodeRegion(crop, null);
                    decoder.recycle();
                    if (part != null) return InputImage.fromBitmap(part, rotation);
                }
            } catch (Exception e) {
                Log.w(TAG, "미리보기 영역 자르기 실패, 전체 이미지 사용", e);
            }
        }
        Image media = image.getImage();
        return media == null ? null : InputImage.fromMediaImage(media, rotation);
    }

    /** 플래시(손전등)를 켜고 끈다. 바뀐 상태를 돌려준다. */
    public boolean toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return false;
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        return torchOn;
    }

    public void close() {
        camera = null;
        imageCapture = null;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTapToFocus() {
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP || camera == null) return true;
            MeteringPoint point = previewView.getMeteringPointFactory()
                    .createPoint(event.getX(), event.getY());
            camera.getCameraControl().startFocusAndMetering(
                    new FocusMeteringAction.Builder(point).build());
            v.performClick();
            return true;
        });
    }
}
