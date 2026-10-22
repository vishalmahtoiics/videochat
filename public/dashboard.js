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

async function check(device, metaEl) {
  metaEl.textContent = "Checking…";
  try {
    const res = await fetch(`/api/location?key=${encodeURIComponent(device.key)}`);
    if (res.status === 404) {
      metaEl.textContent = "No location shared yet (phone app not running?).";
      return;
    }
    if (!res.ok) throw new Error();
    const loc = await res.json();
    metaEl.innerHTML =
      `Last seen ${timeAgo(loc.ts)} · ±${Math.round(loc.accuracy || 0)}m<br />` +
      `${loc.lat.toFixed(5)}, ${loc.lng.toFixed(5)}`;
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
