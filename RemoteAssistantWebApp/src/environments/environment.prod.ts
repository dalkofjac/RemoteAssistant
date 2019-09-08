export const environment = {
  production: true,
  signalingServerUrl: '',
  iceServers: [
    {urls: 'stun:stun.1.google.com:19302'},
    {urls: 'stun:stun1.l.google.com:19302'},
    {urls: 'stun:eu-turn2.xirsys.com'},
    {urls: 'turn:numb.viagenie.ca', username: 'iristicktestapp@gmail.com', credential: 'iristickpw123'},
    {
      username: 'b1GVxPg8uehXP_kK-O7PCeyAzlqjvYBDCY2qNExUAdFQq36y7LFQrJZR1t1-MxsAAAAAAFzlTsNyb2RzLWNvbmVz',
      credential: 'a621caca-7c95-11e9-bfb1-4a049da423ff',
      urls: [
          'turn:eu-turn2.xirsys.com:80?transport=udp',
          'turn:eu-turn2.xirsys.com:3478?transport=udp',
          'turn:eu-turn2.xirsys.com:80?transport=tcp',
          'turn:eu-turn2.xirsys.com:3478?transport=tcp',
          'turns:eu-turn2.xirsys.com:443?transport=tcp',
          'turns:eu-turn2.xirsys.com:5349?transport=tcp'
      ]
    }
  ]
};
