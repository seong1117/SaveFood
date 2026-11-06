package com.savefood.app.ui.scan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * CameraX 연결을 맡는다: 미리보기, 촬영, 탭 초점, 플래시.
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
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
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
