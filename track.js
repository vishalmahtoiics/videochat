// Map viewer. Reads ?key=... and polls the server for that device's latest
// location, moving a marker on the map.

const params = new URLSearchParams(location.search);
const key = params.get("key") || "";

const stateEl = document.getElementById("track-state");
const keyLabel = document.getElementById("key-label");
const updatedEl = document.getElementById("updated");

keyLabel.textContent = key ? `Key: ${key}` : "No key in URL";

const map = L.map("map").setView([20.5937, 78.9629], 4); // India-ish default view
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19,
  attribution: "© OpenStreetMap contributors",
}).addTo(map);

let marker = null;
let accuracyCircle = null;
let firstFix = true;

function setState(text, kind) {
  stateEl.textContent = text;
  stateEl.className = `badge badge-${kind}`;
}

async function poll() {
  if (!key) {
    setState("Add ?key=... to the URL", "ended");
    return;
  }
  try {
    const res = await fetch(`/api/location?key=${encodeURIComponent(key)}`);
    if (res.status === 404) {
      setState("Waiting for location…", "wait");
      return;
    }
    if (!res.ok) throw new Error("bad response");

    const { lat, lng, accuracy, ts } = await res.json();
    const pos = [lat, lng];

    if (!marker) {
      marker = L.marker(pos).addTo(map);
      accuracyCircle = L.circle(pos, { radius: accuracy || 0 }).addTo(map);
    } else {
      marker.setLatLng(pos);
      accuracyCircle.setLatLng(pos).setRadius(accuracy || 0);
    }

    if (firstFix) {
      map.setView(pos, 16);
      firstFix = false;
    }

    const age = Math.round((Date.now() - ts) / 1000);
    setState("Location live", "live");
    const dir = `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
    updatedEl.innerHTML =
      `Last update ${age}s ago · ±${Math.round(accuracy || 0)}m · ` +
      `${new Date(ts).toLocaleString()}<br />` +
      `<a href="${dir}" target="_blank" rel="noopener">➤ Directions in Google Maps</a>`;
  } catch {
    setState("Connection problem…", "ended");
  }
}

poll();
setInterval(poll, 3000);
