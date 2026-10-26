<?php
// Shared auth helpers for the dashboard and the parent-side API calls.
require_once __DIR__ . '/config.php';

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

function is_logged_in(): bool {
    return !empty($_SESSION['auth']);
}

/** For HTML pages: send the visitor to the login page if not signed in. */
function require_login(): void {
    if (!is_logged_in()) {
        header('Location: /login');
        exit;
    }
}

/** For API endpoints the parent uses: refuse with 401 if not signed in. */
function require_api_auth(): void {
    if (!is_logged_in()) {
        http_response_code(401);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'login required']);
        exit;
    }
}
