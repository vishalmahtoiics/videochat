<?php
// Stop sharing (used by the browser share page): drop the last known point.
header('Content-Type: application/json');
$dataDir = __DIR__ . '/../data';
$body = json_decode(file_get_contents('php://input'), true);
$key = (is_array($body) && isset($body['key'])) ? (string)$body['key'] : '';
if ($key !== '') {
    @unlink($dataDir . '/loc_' . sha1($key) . '.json');
}
echo json_encode(['ok' => true]);
