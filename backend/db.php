<?php
// backend/db.php

$host = 'localhost';
$db   = 'scream_db';
$user = 'root';
$pass = ''; // Default for XAMPP/local, user might need to change
$charset = 'utf8mb4';

$dsn = "mysql:host=$host;dbname=$db;charset=$charset";
$options = [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES   => false,
];

try {
    $pdo = new PDO($dsn, $user, $pass, $options);
} catch (\PDOException $e) {
    // Ideally log this, don't show to user in prod
    // throw new \PDOException($e->getMessage(), (int)$e->getCode());
    // For MVP/debugging:
    die("Database Connection Failed: " . $e->getMessage());
}

// Helper function for JSON responses
function sendJson($data, $code = 200) {
    header('Content-Type: application/json');
    http_response_code($code);
    echo json_encode($data);
    exit;
}
?>
