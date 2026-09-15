import fs from "node:fs";
import tls from "node:tls";

const port = Number.parseInt(process.env.UVIR_RELAY_PORT ?? "443", 10);
const certPath = process.env.UVIR_TLS_CERT;
const keyPath = process.env.UVIR_TLS_KEY;

if (!certPath || !keyPath || !Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error("Set UVIR_TLS_CERT, UVIR_TLS_KEY and a valid UVIR_RELAY_PORT.");
}

const channels = new Map();
const maximumHandshakeBytes = 512;

function detach(socket) {
  const registration = socket.uvirRegistration;
  if (!registration) return;
  const channel = channels.get(registration.deviceId);
  if (!channel || channel[registration.role] !== socket) return;
  channel[registration.role] = undefined;
  if (!channel.APP && !channel.SENSOR) channels.delete(registration.deviceId);
}

function closePair(socket) {
  const peer = socket.uvirPeer;
  socket.uvirPeer = undefined;
  if (peer && !peer.destroyed) {
    peer.uvirPeer = undefined;
    peer.destroy();
  }
}

function pairIfReady(deviceId) {
  const channel = channels.get(deviceId);
  if (!channel?.APP || !channel?.SENSOR) return;
  if (channel.APP.destroyed || channel.SENSOR.destroyed) return;

  const app = channel.APP;
  const sensor = channel.SENSOR;
  app.uvirPeer = sensor;
  sensor.uvirPeer = app;

  for (const socket of [app, sensor]) {
    socket.removeListener("data", socket.uvirHandshakeHandler);
    socket.write("UVIR_RELAY_READY\n");
    socket.on("data", (chunk) => {
      const peer = socket.uvirPeer;
      if (peer && !peer.destroyed && !peer.write(chunk)) socket.pause();
    });
    socket.uvirPeer.on("drain", () => socket.resume());
    socket.resume();
  }
}

const server = tls.createServer(
  {
    cert: fs.readFileSync(certPath),
    key: fs.readFileSync(keyPath),
    minVersion: "TLSv1.2"
  },
  (socket) => {
    socket.setKeepAlive(true, 15_000);
    socket.setNoDelay(true);
    socket.setTimeout(45_000, () => socket.destroy());
    let handshake = Buffer.alloc(0);

    const handleHandshake = (chunk) => {
      handshake = Buffer.concat([handshake, chunk]);
      if (handshake.length > maximumHandshakeBytes) return socket.destroy();
      const newline = handshake.indexOf(0x0a);
      if (newline < 0) return;

      const line = handshake.subarray(0, newline).toString("ascii").trim();
      const match = /^UVIR_RELAY (APP|SENSOR) ([A-Fa-f0-9]{12}) ([a-f0-9]{64})$/.exec(line);
      if (!match) return socket.destroy();

      const [, role, deviceId, channelKey] = match;
      const channel = channels.get(deviceId) ?? {};
      if (channel.channelKey && channel.channelKey !== channelKey) {
        return socket.destroy();
      }
      const previous = channel[role];
      if (previous && previous !== socket && !previous.destroyed) previous.destroy();
      channel.channelKey = channelKey;
      channel[role] = socket;
      channels.set(deviceId, channel);
      socket.uvirRegistration = { role, deviceId };
      socket.pause();
      pairIfReady(deviceId);
    };

    socket.uvirHandshakeHandler = handleHandshake;
    socket.on("data", handleHandshake);
    socket.on("close", () => {
      detach(socket);
      closePair(socket);
    });
    socket.on("error", () => {});
  }
);

server.listen(port, "0.0.0.0", () => {
  console.log(`Uvir TLS relay listening on port ${port}`);
});
