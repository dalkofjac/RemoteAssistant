package com.dk.remoteassistantmobileapp.services;

import com.dk.remoteassistantmobileapp.models.IceServer;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class IceServerService {

    public static List<PeerConnection.IceServer> getIceServers() {
        List<IceServer> iceServers = new ArrayList<>();

        // Stun servers
        IceServer iceServerS1 = new IceServer();
        iceServerS1.url = "stun:stun.l.google.com:19302";

        IceServer iceServerS2 = new IceServer();
        iceServerS2.url = "stun:stun1.l.google.com:19302";

        IceServer iceServerS3 = new IceServer();
        iceServerS3.url = "stun:eu-turn2.xirsys.com";

        iceServers.add(iceServerS1);
        iceServers.add(iceServerS2);
        iceServers.add(iceServerS3);

        // Turn servers
        IceServer iceServerT1 = new IceServer();
        iceServerT1.url = "turn:numb.viagenie.ca";
        iceServerT1.username = "iristicktestapp@gmail.com";
        iceServerT1.credential = "iristickpw123";

        IceServer iceServerT2 = new IceServer();
        iceServerT2.url = "turn:eu-turn2.xirsys.com:80?transport=udp";
        iceServerT2.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT2.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        IceServer iceServerT3 = new IceServer();
        iceServerT3.url = "turn:eu-turn2.xirsys.com:3478?transport=udp";
        iceServerT3.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT3.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        IceServer iceServerT4 = new IceServer();
        iceServerT4.url = "turn:eu-turn2.xirsys.com:80?transport=tcp";
        iceServerT4.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT4.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        IceServer iceServerT5 = new IceServer();
        iceServerT5.url = "turn:eu-turn2.xirsys.com:3478?transport=tcp";
        iceServerT5.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT5.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        IceServer iceServerT6 = new IceServer();
        iceServerT6.url = "turns:eu-turn2.xirsys.com:443?transport=tcp";
        iceServerT6.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT6.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        IceServer iceServerT7 = new IceServer();
        iceServerT7.url = "turns:eu-turn2.xirsys.com:5349?transport=tcp";
        iceServerT7.username = "b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz";
        iceServerT7.credential = "a621caca-7c95-11e9-bfb1-4a049da423ff";

        iceServers.add(iceServerT1);
        iceServers.add(iceServerT2);
        iceServers.add(iceServerT3);
        iceServers.add(iceServerT4);
        iceServers.add(iceServerT5);
        iceServers.add(iceServerT6);
        iceServers.add(iceServerT7);

        List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

        for (IceServer iceServer : iceServers) {
            if (iceServer.credential == null) {
                PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                peerIceServers.add(peerIceServer);
            } else {
                PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                        .setUsername(iceServer.username)
                        .setPassword(iceServer.credential)
                        .createIceServer();
                peerIceServers.add(peerIceServer);
            }
        }

        return peerIceServers;
    }
}
