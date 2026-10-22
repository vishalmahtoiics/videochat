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

## Location sharing (find-my-phone)

A small, consent-first location feature is included for tracking **your own**
phone (or a device whose owner knowingly installs it):

- On the phone, open `/share`, enter a private key, and tap **Start sharing**.
  The browser asks for GPS permission; a banner stays visible the whole time.
- On any other device, open `/track?key=YOUR_KEY` to see the phone live on a
  map (OpenStreetMap).
- Sharing stops when you tap **Stop** or close the tab. Nothing is tracked
  silently — the phone always shows it is sharing.

On Android you can open `/share` in Chrome and use **Add to Home screen** to
get an app-like icon. (A browser page shares location only while open; true
always-on background tracking would require a native app with a persistent
notification — not included here.)

The key keeps a device's location private to whoever holds it; locations are
kept only in memory and dropped when sharing stops.

### Parent dashboard

`/dashboard` is a simple parent view: add a device (a name + the same private
key set on the phone), then tap **Check location** to see its latest point, or
**Open live map**. Devices are stored only in your browser (localStorage), not
on the server.

## Native Android app (`android/`)

For background location on a phone (e.g. a parent keeping track of a child's
phone, with the child's knowledge), a native Android app is included under
`android/`. It:

- Runs a **foreground service** that answers location **on demand**: it only
  turns on GPS and sends a location when you tap **Check location** on the
  dashboard. Otherwise nothing is captured or sent. Restarts after reboot.
- Shows a **permanent notification** the whole time it's sharing. The app does
  not hide itself and can be stopped from its own screen. This is required by
  Android and keeps the app honest — it is meant for your own device, or a
  child's device with their knowledge, not covert tracking.

### Building the APK (no Android Studio needed)

A GitHub Actions workflow (`.github/workflows/android.yml`) builds the APK for
you:

1. Push the `android/` folder (already set up in this repo).
2. On GitHub, open the **Actions** tab → the **Build Android APK** run.
3. Download the **`family-locate-debug-apk`** artifact — it contains
   `app-debug.apk`.
4. Copy it to the phone and install (you'll need to allow "install from unknown
   sources").

You can also build locally with Android Studio: open the `android/` folder and
use **Build → Build APK**.

### Setting it up on the phone

1. Install and open the app.
2. Enter your **server address** (e.g. `https://your-domain.com`), a **private
   key**, and an optional device name, then tap **Start sharing location**.
3. Grant location permission, and choose **Allow all the time** so it works
   while the app is closed.
4. On your own device, open `/dashboard` and add the same key to see it.

## License

MIT
