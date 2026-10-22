import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import express from "express";
import { Server } from "socket.io";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;

const app = express();
app.use(express.json());
app.use(express.static(join(__dirname, "public")));

// --- Location sharing (find-my-phone) ---
// In-memory store of the latest location per device key. This is for the
// owner to see their own device; a key keeps it private to whoever holds it.
// Nothing is tracked silently: the phone's own page shows a clear banner and
// a Stop button while it is sharing.
const locations = new Map();

app.post("/api/location", (req, res) => {
  const { key, lat, lng, accuracy } = req.body || {};
  if (!key || typeof lat !== "number" || typeof lng !== "number") {
    return res.status(400).json({ error: "key, lat and lng are required" });
  }
  locations.set(String(key), {
    lat,
    lng,
    accuracy: typeof accuracy === "number" ? accuracy : null,
    ts: Date.now(),
  });
  res.json({ ok: true });
});

app.get("/api/location", (req, res) => {
  const key = String(req.query.key || "");
  const loc = locations.get(key);
  if (!loc) return res.status(404).json({ error: "No location shared yet." });
  res.json(loc);
});

// Owner stops sharing -> remove the last known point.
app.post("/api/location/stop", (req, res) => {
  const key = String(req.body?.key || "");
  locations.delete(key);
  res.json({ ok: true });
});

const httpServer = createServer(app);
const io = new Server(httpServer);

// A room holds at most two peers for a 1:1 call.
const ROOM_CAPACITY = 2;

// Track which socket is in which room so we can clean up on disconnect.
const roomOf = new Map();

function peerCount(room) {
  return io.sockets.adapter.rooms.get(room)?.size ?? 0;
}

io.on("connection", (socket) => {
  socket.on("join", (room) => {
    room = String(room || "").trim();
    if (!room) {
      socket.emit("join-error", "Please enter a room name.");
      return;
    }

    if (peerCount(room) >= ROOM_CAPACITY) {
      socket.emit("room-full", room);
      return;
    }

    socket.join(room);
    roomOf.set(socket.id, room);

    const others = peerCount(room) - 1;
    // Tell the joiner whether someone is already waiting. The second peer
    // to arrive is the one that kicks off the WebRTC offer.
    socket.emit("joined", { room, shouldInitiate: others > 0 });

    // Let the existing peer know a partner has arrived.
    socket.to(room).emit("peer-joined");
  });

  // Relay signaling messages to the other peer in the room. The server
  // never inspects or stores media — it only forwards SDP and ICE.
  socket.on("signal", (data) => {
    const room = roomOf.get(socket.id);
    if (room) socket.to(room).emit("signal", data);
  });

  function leave() {
    const room = roomOf.get(socket.id);
    if (room) {
      socket.to(room).emit("peer-left");
      roomOf.delete(socket.id);
    }
  }

  socket.on("leave", () => {
    const room = roomOf.get(socket.id);
    if (room) socket.leave(room);
    leave();
  });

  socket.on("disconnect", leave);
});

httpServer.listen(PORT, () => {
  console.log(`videochat listening on http://localhost:${PORT}`);
});
