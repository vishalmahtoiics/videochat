// Phone-side location sharing. Sharing only happens after the user taps Start
// and the browser grants GPS permission. A banner stays visible the whole time.

const keyInput = document.getElementById("key");
const startBtn = document.getElementById("start-btn");
const stopBtn = document.getElementById("stop-btn");
const status = document.getElementById("status");
const banner = document.getElementById("sharing-banner");
const trackWrap = document.getElementById("track-link-wrap");
const trackLink = document.getElementById("track-link");

let watchId = null;
let activeKey = null;

// Remember the last key used on this phone for convenience.
try {
  const saved = localStorage.getItem("loc-key");
  if (saved) keyInput.value = saved;
} catch {}

function post(url, body) {
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

startBtn.addEventListener("click", () => {
  const key = keyInput.value.trim();
  if (!key) {
    status.textContent = "Please enter a private key first.";
    return;
  }
  if (!("geolocation" in navigator)) {
    status.textContent = "This browser doesn't support location.";
    return;
  }

  activeKey = key;
  try {
    localStorage.setItem("loc-key", key);
  } catch {}

  // watchPosition triggers the browser's own permission prompt.
  watchId = navigator.geolocation.watchPosition(
    async (pos) => {
      const { latitude, longitude, accuracy } = pos.coords;
      try {
        await post("/api/location", {
          key: activeKey,
          lat: latitude,
          lng: longitude,
          accuracy,
        });
        status.textContent = `Shared: ${latitude.toFixed(5)}, ${longitude.toFixed(
          5
        )} (±${Math.round(accuracy)}m) at ${new Date().toLocaleTimeString()}`;
      } catch {
        status.textContent = "Couldn't reach the server. Retrying…";
      }
    },
    (err) => {
      status.textContent =
        err.code === err.PERMISSION_DENIED
          ? "Location permission was denied. Allow it in your browser to share."
          : `Location error: ${err.message}`;
      stopSharing();
    },
    { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 }
  );

  banner.hidden = false;
  startBtn.hidden = true;
  stopBtn.hidden = false;
  keyInput.disabled = true;

  const url = `${location.origin}/track?key=${encodeURIComponent(key)}`;
  trackLink.href = url;
  trackLink.textContent = url;
  trackWrap.hidden = false;
});

function stopSharing() {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }
  if (activeKey) {
    post("/api/location/stop", { key: activeKey }).catch(() => {});
  }
  banner.hidden = true;
  startBtn.hidden = false;
  stopBtn.hidden = true;
  keyInput.disabled = false;
  status.textContent = "Stopped sharing.";
}

stopBtn.addEventListener("click", stopSharing);

// Stop sharing if the page is closed.
window.addEventListener("beforeunload", () => {
  if (activeKey && watchId !== null) {
    navigator.sendBeacon?.(
      "/api/location/stop",
      new Blob([JSON.stringify({ key: activeKey })], { type: "application/json" })
    );
  }
});
