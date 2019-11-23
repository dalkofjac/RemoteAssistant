'use strict';

var os = require('os');
var nodeStatic = require('node-static');
var http = require('http');
var socketIO = require('socket.io');

var fileServer = new(nodeStatic.Server)();

var server = http.createServer(function(request, response) {
  fileServer.serve(request, response);
});

var port = process.env.PORT || 1794;

server.listen(port);

console.log("Server running at http://localhost:%d", port);

var io = socketIO.listen(server);

io.sockets.on('connection', function(socket) {

    function log() {
      var array = ['Message from server:'];
      array.push.apply(array, arguments);
      socket.emit('log', array);
    }
  
    // if client wants to create or join the room
    socket.on('create or join', function(room) {
      log('Received request to create or join room ' + room);
  
      var roomObject = io.sockets.adapter.rooms[room];
      var numClients = 1;
      if(roomObject !== undefined) {
        numClients = roomObject.length + 1;
      }
      log('Room ' + room + ' now has ' + numClients + ' client(s)');
  
      if (numClients === 1) {
        socket.join(room);
        log('Client ID ' + socket.id + ' created room ' + room);
        socket.emit('created', room, socket.id);
  
      } else if (numClients === 2) {
        socket.join(room);
        log('Client ID ' + socket.id + ' joined room ' + room);
        io.sockets.in(room).emit('joined', room);
      } else {
        socket.emit('full', room);
      }
    });

    // if client wants to send a new message to the room
    socket.on('message', function(message, room) {
      log('Client said: ', message);
      if(socket.rooms[room]) {
        socket.to(room).emit('message', message);
      }
    });
  
    // if client wants to leave the room
    socket.on('bye', function(){
      console.log('received bye');
      socket.disconnect();
    });

});