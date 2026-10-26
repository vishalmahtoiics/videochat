<?php
// Simple two-way chat, keyed by the device's private key.
// GET  /api/chat?key=KEY&since=TS  -> messages (optionally newer than TS)
// POST /api/chat {key, from, text} -> append a message
header('Content-Type: application/json');

$dataDir = __DIR__ . '/../data';
if (!is_dir($dataDir)) { @mkdir($dataDir, 0775, true); }

function chat_file($dir, $key) { return $dir . '/chat_' . sha1($key) . '.json'; }

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $key = isset($_GET['key']) ? (string)$_GET['key'] : '';
    $since = isset($_GET['since']) ? (int)$_GET['since'] : 0;
    if ($key === '') {
        http_response_code(400);
        echo json_encode(['error' => 'key required']);
        exit;
    }
    $f = chat_file($dataDir, $key);
    $msgs = is_file($f) ? json_decode(file_get_contents($f), true) : [];
    if (!is_array($msgs)) { $msgs = []; }
    if ($since > 0) {
        $msgs = array_values(array_filter($msgs, function ($m) use ($since) {
            return isset($m['ts']) && $m['ts'] > $since;
        }));
    }
    echo json_encode(['messages' => $msgs]);
    exit;
}

if ($method === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body)) { $body = []; }
    $key = isset($body['key']) ? (string)$body['key'] : '';
    $from = isset($body['from']) ? trim((string)$body['from']) : '';
    $text = isset($body['text']) ? trim((string)$body['text']) : '';
    if ($key === '' || $text === '') {
        http_response_code(400);
        echo json_encode(['error' => 'key and text required']);
        exit;
    }
    $from = $from !== '' ? mb_substr($from, 0, 40) : 'Unknown';
    $text = mb_substr($text, 0, 2000);

    $f = chat_file($dataDir, $key);
    $msgs = is_file($f) ? json_decode(file_get_contents($f), true) : [];
    if (!is_array($msgs)) { $msgs = []; }
    $msgs[] = ['from' => $from, 'text' => $text, 'ts' => (int)round(microtime(true) * 1000)];
    if (count($msgs) > 200) { $msgs = array_slice($msgs, -200); }
    file_put_contents($f, json_encode($msgs), LOCK_EX);
    echo json_encode(['ok' => true]);
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'method not allowed']);
