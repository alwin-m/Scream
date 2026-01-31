<?php
// actions/create_post.php
require_once '../backend/db.php';
session_start();

header('Content-Type: application/json');

if (!isset($_SESSION['user_id'])) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Check if it's a JSON request (common with fetch) or Form
    $input = json_decode(file_get_contents('php://input'), true);
    $content = trim($input['content'] ?? $_POST['content'] ?? '');
    
    $user_id = $_SESSION['user_id'];

    if (empty($content)) {
        http_response_code(400);
        echo json_encode(['error' => 'Content cannot be empty']);
        exit;
    }

    try {
        $stmt = $pdo->prepare("INSERT INTO tbl_posts (user_id, content) VALUES (:user_id, :content)");
        $stmt->execute(['user_id' => $user_id, 'content' => $content]);
        
        echo json_encode(['success' => true, 'message' => 'Post created']);
        exit;
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(['error' => 'Database error']);
    }
}
?>
