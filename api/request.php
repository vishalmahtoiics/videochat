<?php
// On-demand request endpoint (no Node needed).
// POST /api/request {key}      -> mark a location request pending
// GET  /api/request?key=KEY    -> {pending: true|false}
header('Content-Type: application/json');

$dataDir = __DIR__ . '/../data';
if (!is_dir($dataDir)) { @mkdir($dataDir, 0775, true); }

function req_file($dir, $key) { return $dir . '/req_' . sha1($key) . '.json'; }

$WINDOW_MS = 3 * 60 * 1000; // a request stays pending up to 3 minutes
$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'POST') {
    // Asking the phone to report is a parent action — require login.
    require_once __DIR__ . '/../auth.php';
    require_api_auth();

    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body)) { $body = []; }
    $key = isset($body['key']) ? (string)$body['key'] : '';
    if ($key === '') {
        http_response_code(400);
        echo json_encode(['error' => 'key required']);
        exit;
    }
    file_put_contents(
        req_file($dataDir, $key),
        json_encode(['ts' => (int)round(microtime(true) * 1000)]),
        LOCK_EX
    );
    echo json_encode(['ok' => true]);
    exit;
}

if ($method === 'GET') {
    $key = isset($_GET['key']) ? (string)$_GET['key'] : '';
    $pending = false;
    if ($key !== '') {
        $f = req_file($dataDir, $key);
        if (is_file($f)) {
            $data = json_decode(file_get_contents($f), true);
            $ts = (is_array($data) && isset($data['ts'])) ? (int)$data['ts'] : 0;
            $now = (int)round(microtime(true) * 1000);
            if ($ts && ($now - $ts) < $WINDOW_MS) {
                $pending = true;
            } else {
                @unlink($f);
            }
        }
    }
    echo json_encode(['pending' => $pending]);
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'method not allowed']);
