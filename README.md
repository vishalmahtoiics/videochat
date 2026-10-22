# VideoChat

A simple, consent-first 1:1 video and audio chat app built on WebRTC.

Two people open the same room name, each grants their browser permission to
use the camera and microphone, and the two browsers connect **directly** to
each other. The server only relays the small signaling messages needed to set
up the connection — it never sees or records any audio or video.

## How consent works here

This app is deliberately built so that nobody can be watched or listened to
without knowing:

- **The browser asks.** Camera and microphone access always goes through the
  browser's own permission prompt, which the person controls.
- **It's always visible.** The app shows on-screen indicators for whether your
  camera and mic are on, and standard browser recording indicators stay active
  during the call.
- **You're in control.** Mute, turn the camera off, or leave the call at any
  time. Leaving stops the camera and mic.
- **No hidden access.** There is no remote, silent, or one-click way to turn on
  someone else's camera or mic. Both people join the call knowingly.

## Requirements

- Node.js 18 or newer.
- A modern browser (Chrome, Firefox, Safari, Edge).

## Run it

```bash
npm install
npm start
```

Then open <http://localhost:3000> in two browser tabs (or on two devices),
enter the **same room name** in both, and click **Join call**.

> **Note on HTTPS:** Browsers only allow camera/microphone access on
> `localhost` or over HTTPS. To use this across the internet, run it behind a
> TLS-terminating proxy (or a tunnel such as ngrok) so the page is served over
> `https://`.

## How it works

- `server.js` — an Express server that serves the page and runs a Socket.IO
  signaling relay. Each room holds at most two peers. The server forwards SDP
  and ICE messages between them and nothing else.
- `public/app.js` — the WebRTC client: requests media, creates the peer
  connection, exchanges signaling messages, and wires up the controls and
  indicators.
- `public/index.html` / `public/style.css` — the lobby and call UI.

Media is sent peer-to-peer. A public STUN server
(`stun:stun.l.google.com:19302`) is used to discover network paths; for calls
between restrictive networks you may also need a TURN server, which you can add
to `ICE_SERVERS` in `public/app.js`.

## License

MIT
