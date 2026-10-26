<?php
require_once __DIR__ . '/auth.php';
require_login();
?>
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Chat</title>
    <link rel="stylesheet" href="style.css" />
    <style>
      .chat-wrap { max-width: 640px; margin: 0 auto; padding: 16px; }
      #messages {
        background: var(--panel);
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 12px;
        height: 60vh;
        overflow-y: auto;
      }
      .msg { margin: 6px 0; display: flex; }
      .msg .bubble {
        padding: 8px 12px;
        border-radius: 12px;
        max-width: 80%;
        background: var(--panel-2);
      }
      .msg.me { justify-content: flex-end; }
      .msg.me .bubble { background: var(--accent); color: #06210c; }
      .msg .who { font-size: 0.72rem; color: var(--muted); margin-bottom: 2px; }
      .composer { display: flex; gap: 8px; margin-top: 10px; }
      .composer input { flex: 1; }
    </style>
  </head>
  <body>
    <header class="topbar">
      <h1>Chat</h1>
      <p class="tagline">Messages are visible to both sides.</p>
    </header>

    <section class="chat-wrap">
      <div id="messages"></div>
      <form id="composer" class="composer" autocomplete="off">
        <input id="text" type="text" placeholder="Type a message…" required />
        <button type="submit">Send</button>
      </form>
      <p class="privacy-note"><a href="/dashboard">← Back to dashboard</a></p>
    </section>

    <script>
      const params = new URLSearchParams(location.search);
      const key = params.get("key") || "";
      const ME = "Parent";
      const messagesEl = document.getElementById("messages");
      const textEl = document.getElementById("text");
      let lastTs = 0;

      function esc(s) {
        return s.replace(/[&<>"']/g, (c) => ({
          "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
        }[c]));
      }

      function add(m) {
        const row = document.createElement("div");
        row.className = "msg" + (m.from === ME ? " me" : "");
        row.innerHTML =
          `<div class="bubble"><div class="who">${esc(m.from)}</div>${esc(m.text)}</div>`;
        messagesEl.appendChild(row);
        messagesEl.scrollTop = messagesEl.scrollHeight;
      }

      async function poll() {
        if (!key) return;
        try {
          const res = await fetch(
            `/api/chat?key=${encodeURIComponent(key)}&since=${lastTs}`
          );
          if (!res.ok) return;
          const data = await res.json();
          for (const m of data.messages || []) {
            add(m);
            if (m.ts > lastTs) lastTs = m.ts;
          }
        } catch {}
      }

      document.getElementById("composer").addEventListener("submit", async (e) => {
        e.preventDefault();
        const text = textEl.value.trim();
        if (!text || !key) return;
        textEl.value = "";
        try {
          await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ key, from: ME, text }),
          });
          poll();
        } catch {}
      });

      poll();
      setInterval(poll, 3000);
    </script>
  </body>
</html>
