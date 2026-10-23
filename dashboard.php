<?php
require_once __DIR__ . '/auth.php';
require_login();
?>
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Family dashboard</title>
    <link rel="stylesheet" href="style.css" />
    <style>
      .dash {
        max-width: 640px;
        margin: 0 auto;
        padding: 16px;
      }
      .device {
        background: var(--panel);
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 12px;
      }
      .device h3 {
        margin: 0 0 6px;
      }
      .device .meta {
        color: var(--muted);
        font-size: 0.85rem;
        margin: 4px 0;
      }
      .row {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        margin-top: 8px;
      }
      .row a,
      .row button {
        text-decoration: none;
        display: inline-block;
      }
      .link-btn {
        background: var(--accent);
        color: #06210c;
        padding: 10px 14px;
        border-radius: 8px;
        font-weight: 600;
      }
      .logout {
        text-align: right;
        max-width: 640px;
        margin: 0 auto;
        padding: 0 16px;
      }
      .logout a {
        color: var(--muted);
        font-size: 0.85rem;
      }
    </style>
  </head>
  <body>
    <header class="topbar">
      <h1>Family dashboard</h1>
      <p class="tagline">Your devices are stored only in this browser.</p>
    </header>

    <div class="logout"><a href="/logout">Sign out</a></div>

    <section class="dash">
      <div class="join-form" style="max-width: none">
        <label for="d-name">Add a device</label>
        <input id="d-name" type="text" placeholder="Name, e.g. Aarav's phone" />
        <input id="d-key" type="text" placeholder="Private key (same as on the phone)" />
        <button id="add">Add device</button>
      </div>

      <div id="devices"></div>
      <p id="empty" class="privacy-note">
        No devices yet. Add one above using the same private key you set in the
        phone app.
      </p>
    </section>

    <script src="dashboard.js"></script>
  </body>
</html>
