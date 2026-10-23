<?php
// Location endpoint for shared hosting (no Node needed).
// GET  /api/location?key=KEY  -> latest location for that key, or 404
// POST /api/location {key,lat,lng,accuracy,name} -> save it, clear any request
header('Content-Type: application/json');

$dataDir = __DIR__ . '/../data';
if (!is_dir($dataDir)) { @mkdir($dataDir, 0775, true); }

function loc_file($dir, $key) { return $dir . '/loc_' . sha1($key) . '.json'; }
function req_file($dir, $key) { return $dir . '/req_' . sha1($key) . '.json'; }

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $key = isset($_GET['key']) ? (string)$_GET['key'] : '';
    if ($key === '') {
        http_response_code(400);
        echo json_encode(['error' => 'key required']);
        exit;
    }
    $f = loc_file($dataDir, $key);
    if (!is_file($f)) {
        http_response_code(404);
        echo json_encode(['error' => 'No location shared yet.']);
        exit;
    }
    echo file_get_contents($f);
    exit;
}

if ($method === 'POST') {
    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body)) { $body = []; }

    $key = isset($body['key']) ? (string)$body['key'] : '';
    $lat = isset($body['lat']) && is_numeric($body['lat']) ? (float)$body['lat'] : null;
    $lng = isset($body['lng']) && is_numeric($body['lng']) ? (float)$body['lng'] : null;
    if ($key === '' || $lat === null || $lng === null) {
        http_response_code(400);
        echo json_encode(['error' => 'key, lat and lng are required']);
        exit;
    }

    $rec = [
        'lat' => $lat,
        'lng' => $lng,
        'accuracy' => isset($body['accuracy']) && is_numeric($body['accuracy']) ? (float)$body['accuracy'] : null,
        'name' => isset($body['name']) && is_string($body['name']) ? $body['name'] : '',
        'ts' => (int)round(microtime(true) * 1000),
    ];
    file_put_contents(loc_file($dataDir, $key), json_encode($rec), LOCK_EX);
    @unlink(req_file($dataDir, $key)); // a fresh location answers the request
    echo json_encode(['ok' => true]);
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'method not allowed']);
