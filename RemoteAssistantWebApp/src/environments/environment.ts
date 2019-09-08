// This file can be replaced during build by using the `fileReplacements` array.
// `ng build --prod` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

export const environment = {
  production: false,
  signalingServerUrl: 'http://localhost:1794',
  iceServers: [
    {urls: 'stun:stun.1.google.com:19302'},
    {urls: 'stun:stun1.l.google.com:19302'},
    {urls: 'stun:eu-turn2.xirsys.com'},
    {urls: 'turn:numb.viagenie.ca', username: 'iristicktestapp@gmail.com', credential: 'iristickpw123'}
  ]
};
