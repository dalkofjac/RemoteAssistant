package com.dk.remoteassistantmobileapp.services;

import android.annotation.SuppressLint;
import android.util.Log;

import com.dk.remoteassistantmobileapp.interfaces.SocketIOSignallingInterface;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketIOSignallingClient {
    private static SocketIOSignallingClient mInstance;
    private String mRoomName = null;
    private Socket mSocket;
    private SocketIOSignallingInterface mCallback;

    public boolean isChannelReady = false;
    public boolean isInitiator = false;
    public boolean isStarted = false;

    // This piece of code should not go into production!!!!!
    // This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SocketIOSignallingClient getInstance(String roomName) {
        if (mInstance == null) {
            mInstance = new SocketIOSignallingClient();
        }
        if (mInstance.mRoomName == null) {
            mInstance.mRoomName = roomName;
        }

        return mInstance;
    }

    public void init(SocketIOSignallingInterface signalingInterface) {
        this.mCallback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);

            mSocket = IO.socket("https://remote-assistant-server.azurewebsites.net");
            mSocket.connect();

            Log.d("SignallingClient", "init() called");

            if (!mRoomName.isEmpty()) {
                emitInitStatement(mRoomName);
            }

            //room created event.
            mSocket.on("created", args -> {
                Log.d("SignallingClient", "created call() called with: args = [" + Arrays.toString(args) + "]");
                isInitiator = true;
                mCallback.onCreatedRoom();
            });

            //peer joined event
            mSocket.on("joined", args -> {
                Log.d("SignallingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                mCallback.onNewPeerJoined();
            });

            //room is full event
            mSocket.on("full", args -> {
                Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]");
                mCallback.onRoomFull();
            });

            //log event
            mSocket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event (currently not in use)
            mSocket.on("bye", args -> { });

            //messages - SDP and ICE candidates are transferred through this
            mSocket.on("message", args -> {
                Log.d("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        mCallback.onTryToStart();
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        mCallback.onRemoteHangUp(data);
                    }
                    if(data.startsWith("data:image")) {
                        mCallback.onImageInstructionReceived(data);
                    }
                    if(data.matches("instruction:flashlight")) {
                        mCallback.onFlashlightInstructionReceived();
                    }
                    if(data.matches("instruction:laser")) {
                        mCallback.onLaserInstructionReceived();
                    }
                    if(data.matches("instruction:autofocus")){
                        mCallback.onAutofocusInstructionReceived();
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {
                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            mCallback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            mCallback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            mCallback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.d("SignallingClient", "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        mSocket.emit("create or join", message);
    }

    public void emitMessage(String message) {
        Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
        mSocket.emit("message", message, mRoomName);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            Log.d("emitMessage", obj.toString());
            mSocket.emit("message", obj, mRoomName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            mSocket.emit("message", object, mRoomName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void emitByeMessage() {
        Log.d("SignallingClient", "emitByeMessage() called with: message = [" + "bye" + "]");
        mSocket.emit("bye", mRoomName);
    }

    public void closeSocket() {
        mRoomName = null;
        // wait until all signals are over before closing connection
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        mSocket.disconnect();
                        mSocket.close();
                    }
                }, 1000);
    }
}
