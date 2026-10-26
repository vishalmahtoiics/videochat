# Family Locate

A simple, consent-first way to see **your own** phone's location — or a
child's phone, with their knowledge — only when you ask for it.

- The phone runs a small Android app that shares its location **on demand**:
  it only sends a location when you tap **Check location** on the dashboard.
  A permanent notification is shown while it's on; the app does not hide
  itself.
- The website (this repo's root) is plain **PHP + HTML**, so it runs on any
  ordinary shared hosting (e.g. Hostinger) — no Node.js needed.

## Website files (this repo root → `public_html`)

Upload the contents of this repo's root into your hosting's `public_html`:

```
public_html/
  index.html         landing page
  dashboard.html     parent view (add a device, Check location)
  track.html         live map
  share.html         browser-based sharing (optional, no app)
  *.js, style.css    page scripts and styles
  .htaccess          routes /api/... to the PHP files
  api/               location.php, request.php, stop.php
  data/              stored location/request files (auto-created, web-blocked)
```

After uploading, open `https://your-domain.com` — you should see the
**Family Locate** page.

### How it works

- `POST /api/request {key}` — the dashboard asks the phone to report now.
- The phone polls `GET /api/request?key=…`; only when a request is pending
  does it turn on GPS for a single fix and `POST /api/location`.
- `GET /api/location?key=…` returns the latest point for the dashboard/map.
- A posted location clears the pending request. Data lives in `data/` and is
  blocked from direct web access; it's only served (to whoever knows the key)
  through the API.

## Android app (`android/`)

Source for the on-demand location app. It's built automatically by GitHub
Actions — see the **Actions** tab → latest green **Build Android APK** run →
download the `family-locate-debug-apk` artifact, then install it on the phone.

In the app, set the server to `https://your-domain.com` and a private key,
tap Start, and choose **Allow all the time**. Then open
`https://your-domain.com/dashboard`, add the same key, and tap
**Check location**.

> Intended for your own device, or a child's device with their knowledge —
> not for covert tracking. The app stays visible and shows an ongoing
> notification while sharing is on.

## License

MIT
