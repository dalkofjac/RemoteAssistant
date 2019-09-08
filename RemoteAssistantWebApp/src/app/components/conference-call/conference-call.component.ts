import { Component, OnInit, OnDestroy, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { environment } from 'src/environments/environment';
import * as SocketIOClient from 'socket.io-client';
import { SafeResourceUrl, DomSanitizer } from '@angular/platform-browser';
import { MatSnackBar } from '@angular/material';
import { Constants } from 'src/app/shared/enums/constants';

const pcConfig = {
  iceServers: environment.iceServers,
  sdpSemantics : 'plan-b'
};

@Component({
  selector: 'app-conference-call',
  templateUrl: './conference-call.component.html',
  styleUrls: ['./conference-call.component.scss']
})
export class ConferenceCallComponent implements OnInit, OnDestroy {

  @ViewChild('localVideo', null) localVideo: ElementRef;
  @ViewChild('remoteVideo', null) remoteVideo: ElementRef;
  @ViewChild('remoteVideoTwo', null) remoteVideoTwo: ElementRef;

  localStream: MediaStream;
  remoteStream: MediaStream;
  peerConnection: RTCPeerConnection;

  room: string;
  socket: SocketIOClient.Socket;
  sdpConstraints: RTCOfferOptions;

  remoteStreamTwo: MediaStream;
  remoteVideoSrcTwo: SafeResourceUrl;

  isChannelReady = false;
  isInitiator = false;
  isStarted = false;

  constructor(
    private sanitizer: DomSanitizer,
    private snack: MatSnackBar
  ) { }

  ngOnInit() {
    this.socket = SocketIOClient.connect(environment.signalingServerUrl);
    this.room = Constants.TestingRoom;

    this.socket.emit('create or join', this.room);
    console.log('Attempted to create or  join room', this.room);

    this.defineSocketCommunication();
    this.getUserMedia();
  }

  defineSocketCommunication() {
    const self = this;

    this.socket.on('created', function(room) {
      console.log('Created room ' + room);
      self.isInitiator = true;
    });

    this.socket.on('joined', function(room) {
      console.log('joined: ' + room);
      self.isChannelReady = true;
    });

    this.socket.on('full', function(room) {
      self.snack.open('The room ((' + room + ')) is full. Please choose another one.', 'Dismiss', { duration: 5000 });
      console.log('Room ' + room + ' is full.');
    });

    this.socket.on('log', function(array) {
      console.log.apply(console, array);
    });

    this.socket.on('bye', function() { });

    this.socket.on('message', function(message) {
      console.log('Client received message:', message);

      if (message === 'got user media') {
        self.maybeStart();

      } else if (message.type === 'offer') {
        if (!self.isStarted) {
          self.maybeStart();
        }
        self.peerConnection.setRemoteDescription(new RTCSessionDescription(message));
        self.doAnswer();

      } else if (message.type === 'answer' && self.isStarted) {
        console.log('received answer');
        self.peerConnection.setRemoteDescription(new RTCSessionDescription(message));

      } else if (message.type === 'candidate' && self.isStarted) {
        const candidate = new RTCIceCandidate({
          sdpMLineIndex: message.label,
          candidate: message.candidate
        });
        self.peerConnection.addIceCandidate(candidate);

      } else if (message === 'bye' && self.isStarted) {
        self.handleRemoteHangup();
      }
    });
  }

  getUserMedia() {
    const self = this;

    navigator.mediaDevices.getUserMedia({
      audio: false,
      video: true
    })
    .then((stream: MediaStream) => {
      console.log('Adding local stream.');
      self.localVideo.nativeElement.srcObject = stream;
      self.localVideo.nativeElement.muted = 'muted';
      self.localStream = stream;
      self.sendMessage('got user media');
      if (self.isInitiator) {
        self.maybeStart();
      }
    })
    .catch(function(e) {
      alert('getUserMedia() error: ' + e.name + ': ' + e.message);
    });
  }

  sendMessage(message) {
    const self = this;

    console.log('Client sending message: ', message);
    self.socket.emit('message', message, self.room);
  }

  maybeStart() {
    const self = this;

    console.log('>>>>>>> maybeStart() ', self.isStarted, typeof self.localStream !== 'undefined', self.isChannelReady);
    if (!self.isStarted && typeof self.localStream !== 'undefined' && self.isChannelReady) {
      console.log('>>>>>> creating peer connection');
      self.createPeerConnection();
      self.peerConnection.addTrack(self.localStream.getVideoTracks()[0], self.localStream);
      self.isStarted = true;
      if (self.isInitiator) {
        self.doCall();
      }
    }
  }

  createPeerConnection() {
    const self = this;

    try {
      self.peerConnection = new RTCPeerConnection(pcConfig);

      self.peerConnection.onicecandidate = (event: RTCPeerConnectionIceEvent) => {
        console.log('icecandidate event: ', event);
        if (event.candidate) {
          self.sendMessage({
            type: 'candidate',
            label: event.candidate.sdpMLineIndex,
            id: event.candidate.sdpMid,
            candidate: event.candidate.candidate
          });
        } else {
          console.log('End of candidates.');
        }
      };

      self.peerConnection.ontrack = (event: RTCTrackEvent) => {
        console.log('Remote stream added.');
        if (event.streams[0].id === '112') {
          self.remoteVideoTwo.nativeElement.srcObject = event.streams[0];
          self.remoteStreamTwo = event.streams[0];
        } else {
          self.remoteVideo.nativeElement.srcObject = event.streams[0];
          self.remoteStream = event.streams[0];
        }
      };

      console.log('Successfully created RTCPeerConnnection');

    } catch (e) {
      console.log('Failed to create PeerConnection, exception: ' + e.message);
      alert('Cannot create RTCPeerConnection object.');
      return;
    }
  }

  doCall() {
    const self = this;

    self.sdpConstraints = {};
    self.sdpConstraints.iceRestart = false;
    self.sdpConstraints.offerToReceiveAudio = true;
    self.sdpConstraints.offerToReceiveVideo = true;

    console.log('Sending offer to peer');
    self.peerConnection.createOffer(self.sdpConstraints)
    .then((sdp: RTCSessionDescriptionInit) => {
      self.peerConnection.setLocalDescription(sdp);
      console.log('setLocalAndSendMessage sending message', sdp);
      self.sendMessage(sdp);
    });
  }

  doAnswer() {
    const self = this;

    self.sdpConstraints = {};
    self.sdpConstraints.iceRestart = false;
    self.sdpConstraints.offerToReceiveAudio = true;
    self.sdpConstraints.offerToReceiveVideo = true;

    console.log('Sending answer to peer.');
    self.peerConnection.createAnswer().then((sdp: RTCSessionDescription) => {
      self.peerConnection.setLocalDescription(sdp);
      console.log('setLocalAndSendMessage sending message', sdp);
      self.sendMessage(sdp);
    });
  }

  hangup() {
    console.log('Hanging up.');
    this.stopPeerConnection();
    this.isInitiator = false;
    this.sendMessage('bye');
    this.socket.emit('bye', this.room);
  }

  handleRemoteHangup() {
    console.log('Session terminated.');
    this.stopPeerConnection();
    this.isInitiator = true;
    this.snack.open('Remote client has left the call.', 'Dismiss', { duration: 5000 });
  }

  stopPeerConnection() {
    this.isStarted = false;
    if (this.peerConnection !== undefined && this.peerConnection !== null) {
      this.peerConnection.close();
      this.peerConnection = null;
    }
  }

  ngOnDestroy() {
    this.hangup();
    setTimeout(() => {
      this.socket.disconnect();
    }, 1000);
    if (this.localStream && this.localStream.active) {
      this.localStream.getTracks().forEach(function (track) { track.stop(); });
    }
  }

}
