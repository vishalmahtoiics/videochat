// Consent-first 1:1 WebRTC client.
//
// Flow:
//  1. User types a room name and clicks Join.
//  2. We request camera + mic via getUserMedia — the browser shows its own
//     permission prompt, which the user controls.
//  3. The server pairs us with the one other person in the room and relays
//     SDP/ICE so the two browsers can connect directly (peer-to-peer).
//  4. On-screen indicators always reflect whether the camera/mic are live.

const socket = io();

const ICE_SERVERS = [{ urls: "stun:stun.l.google.com:19302" }];

// --- DOM ---
const lobby = document.getElementById("lobby");
const call = document.getElementById("call");
const joinForm = document.getElementById("join-form");
const roomInput = document.getElementById("room");
const lobbyError = document.getElementById("lobby-error");
const roomLabel = document.getElementById("room-label");
const connState = document.getElementById("connection-state");

const localVideo = document.getElementById("local-video");
const remoteVideo = document.getElementById("remote-video");
const remotePlaceholder = document.getElementById("remote-placeholder");

const localCamInd = document.getElementById("local-cam-ind");
const localMicInd = document.getElementById("local-mic-ind");

const toggleMicBtn = document.getElementById("toggle-mic");
const toggleCamBtn = document.getElementById("toggle-cam");
const hangupBtn = document.getElementById("hangup");

// --- State ---
let localStream = null;
let pc = null;
let currentRoom = null;

function setState(text, kind) {
  connState.textContent = text;
  connState.className = `badge badge-${kind}`;
}

// Create the peer connection and wire up its events.
function createPeerConnection() {
  const connection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

  // Send our local tracks to the peer.
  for (const track of localStream.getTracks()) {
    connection.addTrack(track, localStream);
  }

  connection.onicecandidate = (event) => {
    if (event.candidate) {
      socket.emit("signal", { candidate: event.candidate });
    }
  };

  connection.ontrack = (event) => {
    remoteVideo.srcObject = event.streams[0];
    remotePlaceholder.hidden = true;
  };

  connection.onconnectionstatechange = () => {
    const s = connection.connectionState;
    if (s === "connected") setState("Connected — live", "live");
    else if (s === "disconnected" || s === "failed")
      setState("Connection lost", "ended");
  };

  return connection;
}

async function makeOffer() {
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  socket.emit("signal", { sdp: pc.localDescription });
}

// --- Join flow ---
joinForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  lobbyError.textContent = "";
  const room = roomInput.value.trim();
  if (!room) return;

  try {
    // Ask for camera + mic. The browser prompts the user here.
    localStream = await navigator.mediaDevices.getUserMedia({
      video: true,
      audio: true,
    });
  } catch (err) {
    lobbyError.textContent =
      "Camera/microphone access is needed to join. You can allow it in your browser and try again.";
    return;
  }

  localVideo.srcObject = localStream;
  currentRoom = room;
  socket.emit("join", room);
});

socket.on("join-error", (msg) => {
  lobbyError.textContent = msg;
});

socket.on("room-full", (room) => {
  lobbyError.textContent = `The room "${room}" already has two people. Try a different room name.`;
  stopLocalStream();
});

socket.on("joined", async ({ room, shouldInitiate }) => {
  lobby.hidden = true;
  call.hidden = false;
  roomLabel.textContent = `Room: ${room}`;
  setState(
    shouldInitiate ? "Connecting…" : "Waiting for someone to join…",
    "wait"
  );

  pc = createPeerConnection();

  // The second peer to arrive starts the WebRTC handshake.
  if (shouldInitiate) await makeOffer();
});

socket.on("peer-joined", () => {
  setState("Connecting…", "wait");
});

// --- Signaling relay ---
socket.on("signal", async (data) => {
  if (!pc) return;

  if (data.sdp) {
    await pc.setRemoteDescription(new RTCSessionDescription(data.sdp));
    if (data.sdp.type === "offer") {
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      socket.emit("signal", { sdp: pc.localDescription });
    }
  } else if (data.candidate) {
    try {
      await pc.addIceCandidate(new RTCIceCandidate(data.candidate));
    } catch (err) {
      console.warn("Failed to add ICE candidate", err);
    }
  }
});

socket.on("peer-left", () => {
  setState("Your partner left the call", "ended");
  remoteVideo.srcObject = null;
  remotePlaceholder.hidden = false;
  if (pc) {
    pc.close();
    pc = createPeerConnection();
  }
});

// --- Controls ---
toggleMicBtn.addEventListener("click", () => {
  const track = localStream?.getAudioTracks()[0];
  if (!track) return;
  track.enabled = !track.enabled;
  toggleMicBtn.setAttribute("aria-pressed", String(track.enabled));
  toggleMicBtn.textContent = track.enabled ? "Mute mic" : "Unmute mic";
  localMicInd.className = track.enabled ? "ind ind-on" : "ind";
  localMicInd.title = track.enabled ? "Mic on" : "Mic off";
});

toggleCamBtn.addEventListener("click", () => {
  const track = localStream?.getVideoTracks()[0];
  if (!track) return;
  track.enabled = !track.enabled;
  toggleCamBtn.setAttribute("aria-pressed", String(track.enabled));
  toggleCamBtn.textContent = track.enabled ? "Turn camera off" : "Turn camera on";
  localCamInd.className = track.enabled ? "ind ind-on" : "ind";
  localCamInd.title = track.enabled ? "Camera on" : "Camera off";
});

hangupBtn.addEventListener("click", () => {
  socket.emit("leave");
  cleanup();
  // Back to the lobby.
  call.hidden = true;
  lobby.hidden = false;
  roomInput.value = "";
});

function stopLocalStream() {
  if (localStream) {
    for (const track of localStream.getTracks()) track.stop();
    localStream = null;
  }
}

function cleanup() {
  if (pc) {
    pc.close();
    pc = null;
  }
  stopLocalStream();
  localVideo.srcObject = null;
  remoteVideo.srcObject = null;
  remotePlaceholder.hidden = false;
  currentRoom = null;
}

// Release the camera/mic if the tab is closed.
window.addEventListener("beforeunload", () => {
  socket.emit("leave");
  stopLocalStream();
});
