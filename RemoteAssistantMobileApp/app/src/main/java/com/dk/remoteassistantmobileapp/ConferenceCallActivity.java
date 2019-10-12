package com.dk.remoteassistantmobileapp;

import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dk.remoteassistantmobileapp.helpers.IristickCameraTypes;
import com.dk.remoteassistantmobileapp.huds.InstructionsHud;
import com.dk.remoteassistantmobileapp.interfaces.InstructionsHudActionsListener;
import com.dk.remoteassistantmobileapp.interfaces.SocketIOSignallingInterface;
import com.dk.remoteassistantmobileapp.observers.CustomPeerConnectionObserver;
import com.dk.remoteassistantmobileapp.observers.CustomSdpObserver;
import com.dk.remoteassistantmobileapp.services.IceServerService;
import com.dk.remoteassistantmobileapp.services.IristickCameraCapturer;
import com.dk.remoteassistantmobileapp.services.SocketIOSignallingClient;
import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.IristickBinding;
import com.iristick.smartglass.core.IristickConnection;
import com.iristick.smartglass.support.app.HudActivity;
import com.iristick.smartglass.support.app.HudPresentation;
import com.iristick.smartglass.support.app.IristickApp;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class ConferenceCallActivity extends BaseActivity implements HudActivity, SocketIOSignallingInterface {

    private final ConferenceCallActivity mThisActivity = this;
    private InstructionsHudActionsListener mInstructionsHudListener;

    private PeerConnectionFactory mPeerConnectionFactory;
    private PeerConnection mLocalPeer;
    private EglBase mRootEglBase;
    private Headset mIristickHeadset;
    private MediaConstraints mSdpConstraints;

    private AudioSource mAudioSource;
    private AudioTrack mLocalAudioTrack;

    private VideoSource mVideoSourcePrim;
    private VideoSource mVideoSourceSec;

    private VideoTrack mLocalVideoTrackPrim;
    private VideoTrack mLocalVideoTrackSec;

    private SurfaceViewRenderer mLocalVideoViewPrim;
    private SurfaceViewRenderer mLocalVideoViewSec;

    private IristickCameraCapturer mIristickCameraCapturerPrim;
    private IristickCameraCapturer mIristickCameraCapturerSec;

    private ImageView mImagePeerConnected;
    private TextView mTextPeerConnected;

    private String mRoomName;

    private List<PeerConnection.IceServer> mPeerIceServers = new ArrayList<>();

    private boolean mHeadsetConnected = false;
    private boolean mGotUserMedia = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_call);

        Bundle extras = getIntent().getExtras();
        mRoomName = extras.getString("ROOM_NAME");
        if(mRoomName == null) { mRoomName = "testroom"; }

        // Make sure screen is always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ButterKnife.bind(this);

        initViews();
        initVideos();

        mPeerIceServers = IceServerService.getIceServers();
        SocketIOSignallingClient.getInstance(mRoomName).init(this);
        start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IristickApp.registerConnectionListener(mIristickListener, null);
    }

    @Override
    protected void onStop() {
        IristickApp.unregisterConnectionListener(mIristickListener);
        super.onStop();
    }

    @Override
    public HudPresentation onCreateHudPresentation(Display display) {
        InstructionsHud hud = new InstructionsHud(this, display);
        mInstructionsHudListener = hud;
        return hud;
    }

    private void initViews() {
        mLocalVideoViewPrim = (SurfaceViewRenderer) findViewById(R.id.local_gl_surface_view_center);
        mLocalVideoViewSec = (SurfaceViewRenderer) findViewById(R.id.local_gl_surface_view_zoom);
        mImagePeerConnected = (ImageView) findViewById(R.id.iV_peer_connected);
        mTextPeerConnected = (TextView) findViewById(R.id.tV_peer_connected);
    }

    private void initVideos() {
        mRootEglBase = EglBase.create();

        mLocalVideoViewPrim.init(mRootEglBase.getEglBaseContext(), null);
        mLocalVideoViewSec.init(mRootEglBase.getEglBaseContext(), null);

        mLocalVideoViewPrim.setZOrderMediaOverlay(true);
        mLocalVideoViewSec.setZOrderMediaOverlay(true);
    }

    private void start() {
        // Check for Iristick glasses
        mIristickHeadset = IristickApp.getHeadset();

        if (mIristickHeadset == null) {
            showToast("Waiting for headset...");
            return;
        } else {
            mHeadsetConnected = true;
        }

        // Initialize the PeerConnectionFactory globals
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Create a new PeerConnectionFactory instance
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 16; // ADAPTER_TYPE_LOOPBACK
        options.disableNetworkMonitor = true;

        mPeerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(mRootEglBase.getEglBaseContext(), false, false))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext()))
                .createPeerConnectionFactory();


        mIristickCameraCapturerPrim = new IristickCameraCapturer(mIristickHeadset, null, IristickCameraTypes.CENTER_CAMERA);
        mIristickCameraCapturerSec = new IristickCameraCapturer(mIristickHeadset, null, IristickCameraTypes.ZOOM_CAMERA);

        // Create a VideoSource instance
        mVideoSourcePrim = mPeerConnectionFactory.createVideoSource(false);
        mVideoSourceSec = mPeerConnectionFactory.createVideoSource(false);

        mLocalVideoTrackPrim = mPeerConnectionFactory.createVideoTrack("100", mVideoSourcePrim);
        mLocalVideoTrackSec = mPeerConnectionFactory.createVideoTrack("110", mVideoSourceSec);

        // Create an AudioSource instance
        MediaConstraints mAudioConstraints = new MediaConstraints();
        mAudioSource = mPeerConnectionFactory.createAudioSource(mAudioConstraints);
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack("101", mAudioSource);

        mLocalVideoViewPrim.setVisibility(View.VISIBLE);
        mLocalVideoViewSec.setVisibility(View.VISIBLE);

        // Initialize the VideoCapturer
        SurfaceTextureHelper surfaceTextureHelperPrim = SurfaceTextureHelper.create("CaptureThreadOne", mRootEglBase.getEglBaseContext());
        SurfaceTextureHelper surfaceTextureHelperSec = SurfaceTextureHelper.create("CaptureThreadTwo", mRootEglBase.getEglBaseContext());

        mIristickCameraCapturerPrim.initialize(surfaceTextureHelperPrim, this, mVideoSourcePrim.getCapturerObserver());
        mIristickCameraCapturerSec.initialize(surfaceTextureHelperSec, this, mVideoSourceSec.getCapturerObserver());

        // Start capturing the video from the camera (params: width, height and fps)
        mIristickCameraCapturerPrim.startCapture(1920, 1080, 30);
        mIristickCameraCapturerSec.startCapture(1280, 720, 30);

        mLocalVideoViewPrim.setMirror(false);
        mLocalVideoViewSec.setMirror(false);

        mLocalVideoTrackPrim.addSink(mLocalVideoViewPrim);
        mLocalVideoTrackSec.addSink(mLocalVideoViewSec);

        mGotUserMedia = true;

        SocketIOSignallingClient.getInstance(mRoomName).emitMessage("got user media");

        if (SocketIOSignallingClient.getInstance(mRoomName).isInitiator) {
            onTryToStart();
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!SocketIOSignallingClient.getInstance(mRoomName).isStarted && mLocalVideoViewPrim != null && mLocalVideoViewSec != null && SocketIOSignallingClient.getInstance(mRoomName).isChannelReady) {
                createPeerConnection();
                SocketIOSignallingClient.getInstance(mRoomName).isStarted = true;
                if (SocketIOSignallingClient.getInstance(mRoomName).isInitiator) {
                    doCall();
                }
            }
        });
    }

    /**
     * Creating the local PeerConnection instance
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mPeerIceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        mLocalPeer = mPeerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(mLocalPeer, iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received Remote stream");
                super.onAddStream(mediaStream);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
            }
        });

        addStreamToLocalPeer();
    }

    /**
     * Adding the stream to the LocalPeer
     */
    private void addStreamToLocalPeer() {
        MediaStream streamPrim = mPeerConnectionFactory.createLocalMediaStream("102");
        streamPrim.addTrack(mLocalAudioTrack);
        streamPrim.addTrack(mLocalVideoTrackPrim);
        mLocalPeer.addStream(streamPrim);

        MediaStream streamSec = mPeerConnectionFactory.createLocalMediaStream("112");
        streamSec.addTrack(mLocalVideoTrackSec);
        mLocalPeer.addStream(streamSec);
    }

    /**
     * This method is called when the app is initiator
     * It generates the offer and sends it over through socket to remote peer
     */
    private void doCall() {
        mSdpConstraints = new MediaConstraints();
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "false"));
        CustomSdpObserver sdpObserver = new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");
                SocketIOSignallingClient.getInstance(mRoomName).emitMessage(sessionDescription);
            }
        };
        mLocalPeer.createOffer(sdpObserver, mSdpConstraints);
    }

    /**
     * Received local ice candidate
     * Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(PeerConnection peer, IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SocketIOSignallingClient.getInstance(mRoomName).emitIceCandidate(iceCandidate);
    }

    /**
     * SignallingCallback - called when the room is created - android client is the initiator
     */
    @Override
    public void onCreatedRoom() {
        showToast("You created the room. Got user media: " + mGotUserMedia);
    }

    /**
     * SignallingCallback - called when you join the room - web app is the initiator
     */
    @Override
    public void onJoinedRoom() {
        showToast("You joined the room. Got user media: " + mGotUserMedia);
    }

    @Override
    public void onNewPeerJoined() {
        showToast("Remote Peer Joined");
    }

    @Override
    public void onRemoteHangUp(String msg) {
        showToast("Remote Peer hungup");
        runOnUiThread(this::handleRemoteHangup);
    }

    @Override
    public void onRoomFull() {
        showToast("The room is full. Please choose another one.");
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {
            if (!SocketIOSignallingClient.getInstance(mRoomName).isStarted) {
                onTryToStart();
            }
            try {
                mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                updateViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        mSdpConstraints = new MediaConstraints();
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "false"));
        mLocalPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SocketIOSignallingClient.getInstance(mRoomName).emitMessage(sessionDescription);
            }
        }, mSdpConstraints);
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */
    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received Answer");
        try {
            mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                    new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()),
                            data.getString("sdp")));
            updateViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateViews(boolean peerConnected) {
        runOnUiThread(() -> {
            if(peerConnected) {
                mImagePeerConnected.setVisibility(View.VISIBLE);
                mTextPeerConnected.setVisibility(View.VISIBLE);
            } else {
                mImagePeerConnected.setVisibility(View.INVISIBLE);
                mTextPeerConnected.setVisibility(View.INVISIBLE);
            }
        });
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            mLocalPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageInstructionReceived(String imageData) {
        mInstructionsHudListener.onDisplayImageReceived(imageData);
    }

    @Override
    public void onFlashlightInstructionReceived() {
        mIristickCameraCapturerPrim.triggerTorch();
    }

    @Override
    public void onLaserInstructionReceived() {
        mIristickCameraCapturerPrim.triggerLaser();
    }

    @Override
    public void onAutofocusInstructionReceived() {
        mIristickCameraCapturerSec.triggerAF();
    }

    @Override
    public void onZoomInstructionReceived() {
        mIristickCameraCapturerSec.triggerZoom();
    }

    @OnClick(R.id.btn_end_call)
    public void onBtnHangupClicked() {
        hangup();
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
        hangup();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if(mHeadsetConnected) {
            hangup();
            PeerConnectionFactory.shutdownInternalTracer();
            mRootEglBase.release();

            mIristickCameraCapturerPrim.closeCameraBeforeShutDown();
            mIristickCameraCapturerSec.closeCameraBeforeShutDown();
        }
        SocketIOSignallingClient.getInstance(mRoomName).closeSocket();
        super.onDestroy();
    }

    private void hangup() {
        stop();
        mIristickCameraCapturerPrim.resetCamerasSettingsToDefault();
        SocketIOSignallingClient.getInstance(mRoomName).isInitiator = false;
        SocketIOSignallingClient.getInstance(mRoomName).emitMessage("bye");
        SocketIOSignallingClient.getInstance(mRoomName).emitByeMessage();
    }

    private void handleRemoteHangup() {
        stop();
        mInstructionsHudListener.onClientHangUp();
        mIristickCameraCapturerPrim.resetCamerasSettingsToDefault();
        SocketIOSignallingClient.getInstance(mRoomName).isInitiator = true;
    }

    private void stop() {
        try {
            if(mLocalPeer != null) {
                mLocalPeer.close();
                mLocalPeer = null;
            }
            SocketIOSignallingClient.getInstance(mRoomName).isStarted = false;
            updateViews(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private final IristickConnection mIristickListener = new IristickConnection() {
        @Override
        public void onHeadsetConnected(Headset headset) {
        }

        @Override
        public void onHeadsetDisconnected(Headset headset) {
            mThisActivity.finish();
        }

        @Override
        public void onIristickServiceInitialized(IristickBinding binding) {
        }

        @Override
        public void onIristickServiceError(int error) {
        }
    };
}
