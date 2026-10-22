// Parent dashboard. Device names + keys are kept only in this browser
// (localStorage). Clicking a device fetches its latest reported location.

const STORE = "family-devices";
const devicesEl = document.getElementById("devices");
const emptyEl = document.getElementById("empty");
const nameInput = document.getElementById("d-name");
const keyInput = document.getElementById("d-key");

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORE) || "[]");
  } catch {
    return [];
  }
}

function save(list) {
  try {
    localStorage.setItem(STORE, JSON.stringify(list));
  } catch {}
}

function timeAgo(ts) {
  const s = Math.round((Date.now() - ts) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  return `${Math.round(s / 3600)} h ago`;
}

function show(loc, metaEl) {
  metaEl.innerHTML =
    `Updated ${timeAgo(loc.ts)} · ±${Math.round(loc.accuracy || 0)}m<br />` +
    `${loc.lat.toFixed(5)}, ${loc.lng.toFixed(5)}`;
}

async function latest(key) {
  const res = await fetch(`/api/location?key=${encodeURIComponent(key)}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error();
  return res.json();
}

// Ask the phone to report now, then wait for a fresh point to arrive.
async function check(device, metaEl) {
  metaEl.textContent = "Requesting the phone…";
  try {
    const before = await latest(device.key).catch(() => null);
    const baseTs = before ? before.ts : 0;

    await fetch("/api/request", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ key: device.key }),
    });

    // Poll for a newer location than we had before (phone answers within ~15s).
    const deadline = Date.now() + 45000;
    while (Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 3000));
      const loc = await latest(device.key).catch(() => null);
      if (loc && loc.ts > baseTs) {
        show(loc, metaEl);
        return;
      }
      metaEl.textContent = "Waiting for the phone to respond…";
    }
    metaEl.textContent =
      "No response yet. Make sure the app is installed, open at least once, and online.";
  } catch {
    metaEl.textContent = "Couldn't reach the server.";
  }
}

function render() {
  const list = load();
  devicesEl.innerHTML = "";
  emptyEl.hidden = list.length > 0;

  list.forEach((device, i) => {
    const card = document.createElement("div");
    card.className = "device";

    const h = document.createElement("h3");
    h.textContent = device.name || device.key;
    card.appendChild(h);

    const meta = document.createElement("p");
    meta.className = "meta";
    meta.textContent = "Tap “Check location”.";
    card.appendChild(meta);

    const row = document.createElement("div");
    row.className = "row";

    const checkBtn = document.createElement("button");
    checkBtn.className = "link-btn";
    checkBtn.textContent = "Check location";
    checkBtn.addEventListener("click", () => check(device, meta));
    row.appendChild(checkBtn);

    const mapLink = document.createElement("a");
    mapLink.className = "ctrl";
    mapLink.style.padding = "10px 14px";
    mapLink.style.borderRadius = "8px";
    mapLink.href = `/track?key=${encodeURIComponent(device.key)}`;
    mapLink.target = "_blank";
    mapLink.rel = "noopener";
    mapLink.textContent = "Open live map";
    row.appendChild(mapLink);

    const del = document.createElement("button");
    del.className = "ctrl ctrl-danger";
    del.textContent = "Remove";
    del.addEventListener("click", () => {
      const l = load();
      l.splice(i, 1);
      save(l);
      render();
    });
    row.appendChild(del);

    card.appendChild(row);
    devicesEl.appendChild(card);
  });
}

document.getElementById("add").addEventListener("click", () => {
  const name = nameInput.value.trim();
  const key = keyInput.value.trim();
  if (!key) return;
  const list = load();
  list.push({ name, key });
  save(list);
  nameInput.value = "";
  keyInput.value = "";
  render();
});

render();
