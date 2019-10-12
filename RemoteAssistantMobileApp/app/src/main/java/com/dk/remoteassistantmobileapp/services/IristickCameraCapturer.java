package com.dk.remoteassistantmobileapp.services;

import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CameraDevice;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.iristick.smartglass.core.camera.CaptureSession;

import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.List;

public class IristickCameraCapturer implements CameraVideoCapturer {
    private static final String TAG = "IristickCameraCapturer";

    private final Headset mHeadset;
    private final CameraEventsHandler mEvents;
    private final String[] mCameraNames;
    private String mChosenCameraName;

    private SurfaceTextureHelper mSurfaceHelper;
    private Context mContext;
    private CapturerObserver mObserver;
    private Handler mCameraThreadHandler;

    private Point mFrameSize;
    private final Object mStateLock = new Object();

    private boolean mSessionOpening;
    private boolean mStopping;
    private int mFailureCount;
    private int mWidth;
    private int mHeight;
    private int mFrameRate;

    private CameraDevice mCamera;
    private Surface mSurface;
    private CaptureSession mCaptureSession;
    private boolean mFirstFrameObserved;

    private final float mZoomDefault = 1.0f;
    private float mZoomMax = 1.0f;

    private boolean mHasAutoFocus = false;
    private boolean mTorch = false;
    private boolean mLaser = false;
    private boolean mZoom = false;

    public IristickCameraCapturer(Headset headset, CameraEventsHandler eventsHandler, String cameraName) {
        if (eventsHandler == null) {
            eventsHandler = new CameraEventsHandler() {
                @Override
                public void onCameraError(String s) {}
                @Override
                public void onCameraDisconnected() {}
                @Override
                public void onCameraFreezed(String s) {}
                @Override
                public void onCameraOpening(String s) {}
                @Override
                public void onFirstFrameAvailable() {}
                @Override
                public void onCameraClosed() {}
            };
        }
        mHeadset = headset;
        mEvents = eventsHandler;
        mCameraNames = headset.getCameraIdList();
        mChosenCameraName = cameraName;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        mSurfaceHelper = surfaceTextureHelper;
        mContext = context;
        mObserver = capturerObserver;
        mCameraThreadHandler = surfaceTextureHelper.getHandler();

        String cameraId = mChosenCameraName;

        CameraCharacteristics characteristics = mHeadset.getCameraCharacteristics(cameraId);
        mZoomMax = characteristics.get(CameraCharacteristics.SCALER_MAX_ZOOM);

        CameraCharacteristics.StreamConfigurationMap streams = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Point[] sizes = streams.getSizes();
        mFrameSize = sizes[sizes.length - 1];

        if (characteristics.containsKey(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)) {
            int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            for (int afMode : afModes) {
                if (afMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                    mHasAutoFocus = true;
                    break;
                }
            }
        }
    }

    @Override
    public void startCapture(int width, int height, int frameRate) {
        Log.d(TAG, "startCapture: " + width + "x" + height + "@" + frameRate);

        if (mContext == null) {
            throw new IllegalStateException("CameraCapturer must be initialized before calling startCapture");
        }

        synchronized (mStateLock) {
            if (mSessionOpening || mCaptureSession != null) {
                Log.w(TAG, "Capture already started");
                return;
            }

            mWidth = width;
            mHeight = height;
            mFrameRate = frameRate;

            openCamera(true);
        }
    }

    private void openCamera(boolean resetFailures) {
        synchronized (mStateLock) {
            if (resetFailures) {
                mFailureCount = 0;
            }
            mSessionOpening = true;
            mCameraThreadHandler.post(() -> {
                synchronized (mStateLock) {
                    final String name = mChosenCameraName;
                    mEvents.onCameraOpening(name);
                    try {
                        mHeadset.openCamera(name, mCameraListener, mCameraThreadHandler);
                    } catch (IllegalArgumentException e) {
                        mEvents.onCameraError("Unknown camera: " + name);
                    }
                }
            });
        }
    }

    @Override
    public void stopCapture() {
        Log.d(TAG, "stopCapture");

        synchronized (mStateLock) {
            mStopping = true;
            while (mSessionOpening) {
                Log.d(TAG, "stopCapture: Waiting for session to open");
                try {
                    mStateLock.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "stopCapture: Interrupted while waiting for session to open");
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (mCaptureSession != null) {
                closeCamera();
                mObserver.onCapturerStopped();
            } else {
                Log.d(TAG, "stopCapture: No session open");
            }

            mStopping = false;
        }

        Log.d(TAG, "stopCapture: Done");
    }

    private void closeCamera() {
        synchronized (mStateLock) {
            final CameraDevice camera = mCamera;
            final Surface surface = mSurface;
            mCameraThreadHandler.post(() -> {
                mSurfaceHelper.stopListening();
                try {
                    camera.close();
                } catch (IllegalStateException e) {
                    // ignore
                }
                surface.release();
            });
            mCaptureSession = null;
            mSurface = null;
            mCamera = null;
        }
    }

    @Override
    public void changeCaptureFormat(int width, int height, int frameRate) {
        synchronized (mStateLock) {
            stopCapture();
            startCapture(width, height, frameRate);
        }
    }

    @Override
    public void dispose() {
        stopCapture();
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    @Override
    public void switchCamera(final CameraSwitchHandler cameraSwitchHandler) {
        throw new UnsupportedOperationException();
    }

    private void checkIsOnCameraThread() {
        if(Thread.currentThread() != mCameraThreadHandler.getLooper().getThread()) {
            Log.e(TAG, "Check is on camera thread failed.");
            throw new RuntimeException("Not on camera thread.");
        }
    }

    private void handleFailure(String error) {
        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (mSessionOpening) {
                if (mCamera != null) {
                    mCamera.close();
                    mCamera = null;
                    mSurface.release();
                    mSurface = null;
                }
                mObserver.onCapturerStarted(false);
                mSessionOpening = false;
                mStateLock.notifyAll();
            }
            if ("Disconnected".equals(error)) {
                mEvents.onCameraDisconnected();
                if (!mStopping)
                    stopCapture();
            } else if (mFailureCount < 3 && !mStopping) {
                mFailureCount++;
                mCameraThreadHandler.postDelayed(() -> openCamera(false), 200);
            } else {
                mEvents.onCameraError(error);
                if (!mStopping)
                    stopCapture();
            }
        }
    }

    private void applyParametersInternal() {
        Log.d(TAG, "applyParametersInternal");

        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (mSessionOpening || mStopping || mCaptureSession == null) {
                return;
            }

            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L / mFrameRate);
            setupCaptureRequest(builder);
            mCaptureSession.setRepeatingRequest(builder.build(), null, null);
        }
    }

    private void setupCaptureRequest(CaptureRequest.Builder builder) {
        // zoom is currently always set to default value (1)
        builder.set(CaptureRequest.SCALER_ZOOM, mZoomDefault);

        // auto-focus is only required on zoom camera
        if (mHasAutoFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        }
    }

    public void triggerAF() {
        mCameraThreadHandler.post(this::triggerAFInternal);
    }

    public void triggerTorch() {
        mTorch = !mTorch;
        mCameraThreadHandler.post(this::triggerTorchInternal);
    }

    public void triggerLaser() {
        mLaser = !mLaser;
        mCameraThreadHandler.post(this::triggerLaserInternal);
    }

    public void triggerZoom() {
        mZoom = !mZoom;
        mCameraThreadHandler.post(this::triggerZoomInternal);
    }

    public void closeCameraBeforeShutDown(){
        mCameraThreadHandler.post(this::closeCamera);
    }


    private void triggerAFInternal() {
        Log.d(TAG, "triggerAFInternal");

        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (!mHasAutoFocus || mSessionOpening || mStopping || mCaptureSession == null) {
                return;
            }

            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
            setupCaptureRequest(builder);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCaptureSession.capture(builder.build(), null, null);
        }
    }

    private void triggerTorchInternal() {
        Log.d(TAG, "triggerTorch");

        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (mSessionOpening || mStopping || mCaptureSession == null) {
                return;
            }

            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
            setupCaptureRequest(builder);
            mHeadset.setTorchMode(mTorch);
            mCaptureSession.setRepeatingRequest(builder.build(), null, null);
        }
    }

    private void triggerLaserInternal() {
        Log.d(TAG, "triggerLaser");

        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (mSessionOpening || mStopping || mCaptureSession == null) {
                return;
            }

            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
            setupCaptureRequest(builder);
            mHeadset.setLaserPointer(mLaser);
            mCaptureSession.setRepeatingRequest(builder.build(), null, null);
        }
    }

    private void triggerZoomInternal() {
        Log.d(TAG, "triggerZoomInternal");

        checkIsOnCameraThread();
        synchronized (mStateLock) {
            if (!mHasAutoFocus || mSessionOpening || mStopping || mCaptureSession == null) {
                return;
            }

            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
            setupCaptureRequest(builder);
            builder.set(CaptureRequest.SCALER_ZOOM, mZoom ? mZoomMax : mZoomDefault);
            mCaptureSession.setRepeatingRequest(builder.build(), null, null);
        }
    }

    public void resetCamerasSettingsToDefault() {
        if(mTorch) {
            triggerTorch();
        }
        if(mLaser) {
            triggerLaser();
        }
        if(mZoom) {
            triggerZoom();
        }
    }

    private final VideoSink mSink = new VideoSink() {
        @Override
        public void onFrame(VideoFrame frame) {
            checkIsOnCameraThread();
            synchronized (mStateLock) {
                if (mCaptureSession == null) {
                    return;
                }
                if (!mFirstFrameObserved) {
                    mEvents.onFirstFrameAvailable();
                    mFirstFrameObserved = true;
                }
                mObserver.onFrameCaptured(frame);
            }
        }
    };

    // Event Listeners //

    private final CameraDevice.Listener mCameraListener = new CameraDevice.Listener() {
        @Override
        public void onOpened(CameraDevice device) {
            checkIsOnCameraThread();
            synchronized (mStateLock) {
                mCamera = device;

                mSurfaceHelper.setTextureSize(mWidth, mHeight);
                mSurface = new Surface(mSurfaceHelper.getSurfaceTexture());

                List<Surface> outputs = new ArrayList<>();
                outputs.add(mSurface);
                mCamera.createCaptureSession(outputs, mCaptureSessionListener, mCameraThreadHandler);
            }
        }

        @Override
        public void onClosed(CameraDevice device) {}

        @Override
        public void onDisconnected(CameraDevice device) {
            checkIsOnCameraThread();
            synchronized (mStateLock) {
                if (mCamera == device || mCamera == null) {
                    handleFailure("Disconnected");
                } else {
                    Log.w(TAG, "onDisconnected from another CameraDevice");
                }
            }
        }

        @Override
        public void onError(CameraDevice device, int error) {
            checkIsOnCameraThread();
            synchronized (mStateLock) {
                if (mCamera == device || mCamera == null) {
                    handleFailure("Camera device error " + error);
                } else {
                    Log.w(TAG, "onError from another CameraDevice");
                }
            }
        }
    };

    private final CaptureSession.Listener mCaptureSessionListener = new CaptureSession.Listener() {
        @Override
        public void onConfigured(CaptureSession session) {
            checkIsOnCameraThread();
            synchronized (mStateLock) {
                mSurfaceHelper.startListening(mSink);
                mObserver.onCapturerStarted(true);
                mSessionOpening = false;
                mCaptureSession = session;
                mFirstFrameObserved = false;
                mStateLock.notifyAll();
                if (mHasAutoFocus) {
                    mCameraThreadHandler.removeCallbacks(IristickCameraCapturer.this::triggerAFInternal);
                    mCameraThreadHandler.postDelayed(IristickCameraCapturer.this::triggerAFInternal, 500);
                }
                applyParametersInternal();
            }
        }

        @Override
        public void onConfigureFailed(CaptureSession session, int number) {
            handleFailure("Error while creating capture session");
        }

        @Override
        public void onClosed(CaptureSession session) {}

        @Override
        public void onActive(CaptureSession session) {}

        @Override
        public void onCaptureQueueEmpty(CaptureSession session) {}

        @Override
        public void onReady(CaptureSession session) {}
    };
}
