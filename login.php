<?php
require_once __DIR__ . '/auth.php';

if (is_logged_in()) {
    header('Location: /dashboard');
    exit;
}

$error = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $u = isset($_POST['username']) ? (string)$_POST['username'] : '';
    $p = isset($_POST['password']) ? (string)$_POST['password'] : '';
    if (hash_equals($ADMIN_USER, $u) && hash_equals($ADMIN_PASS, $p)) {
        session_regenerate_id(true);
        $_SESSION['auth'] = true;
        header('Location: /dashboard');
        exit;
    }
    $error = 'Wrong username or password.';
}
?>
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Sign in</title>
    <link rel="stylesheet" href="style.css" />
  </head>
  <body>
    <header class="topbar">
      <h1>Sign in</h1>
      <p class="tagline">Enter your details to open the dashboard.</p>
    </header>

    <section class="call" style="max-width: 420px">
      <form class="join-form" style="max-width: none" method="post" action="/login">
        <label for="username">Username</label>
        <input id="username" name="username" type="text" autocomplete="username" required />

        <label for="password">Password</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required />

        <button type="submit">Sign in</button>
        <?php if ($error !== ''): ?>
          <p class="error" role="alert"><?php echo htmlspecialchars($error, ENT_QUOTES); ?></p>
        <?php endif; ?>
      </form>
    </section>
  </body>
</html>
