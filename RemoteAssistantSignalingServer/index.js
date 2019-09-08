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

    // convenience function to log server messages on the client
    function log() {
      var array = ['Message from server:'];
      array.push.apply(array, arguments);
      socket.emit('log', array);
    }
  
    socket.on('message', function(message, room) {
      log('Client said: ', message);
      if(socket.rooms[room]) {
        socket.to(room).emit('message', message);
      } 
    });
  
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
  
    socket.on('ipaddr', function() {
      var ifaces = os.networkInterfaces();
      for (var dev in ifaces) {
        ifaces[dev].forEach(function(details) {
          if (details.family === 'IPv4' && details.address !== '127.0.0.1') {
            socket.emit('ipaddr', details.address);
          }
        });
      }
    });
  
    socket.on('bye', function(){
      console.log('received bye');
      socket.disconnect();
    });

});