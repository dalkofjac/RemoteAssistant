package com.dk.remoteassistantmobileapp.interfaces;

import org.json.JSONObject;

public interface SocketIOSignallingInterface {

    void onRemoteHangUp(String msg);

    void onOfferReceived(JSONObject data);

    void onAnswerReceived(JSONObject data);

    void onIceCandidateReceived(JSONObject data);

    void onTryToStart();

    void onCreatedRoom();

    void onJoinedRoom();

    void onNewPeerJoined();

    void onRoomFull();

    void onImageReceived(String imageData);

    void onTorchInstructionReceived();

    void onLaserInstructionReceived();
}
