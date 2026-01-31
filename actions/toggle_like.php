<?php
// actions/toggle_like.php
require_once '../backend/db.php';
session_start();

header('Content-Type: application/json');

if (!isset($_SESSION['user_id'])) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit;
}

$input = json_decode(file_get_contents('php://input'), true);
$post_id = $input['post_id'] ?? 0;
$user_id = $_SESSION['user_id'];

if (!$post_id) {
    http_response_code(400);
    echo json_encode(['error' => 'Invalid Post ID']);
    exit;
}

try {
    // Check if like exists
    $check = $pdo->prepare("SELECT id FROM tbl_likes WHERE post_id = :post_id AND user_id = :user_id");
    $check->execute(['post_id' => $post_id, 'user_id' => $user_id]);
    $exists = $check->fetchColumn();

    if ($exists) {
        // Unlike
        $stmt = $pdo->prepare("DELETE FROM tbl_likes WHERE post_id = :post_id AND user_id = :user_id");
        $stmt->execute(['post_id' => $post_id, 'user_id' => $user_id]);
        $liked = false;
    } else {
        // Like
        $stmt = $pdo->prepare("INSERT INTO tbl_likes (post_id, user_id) VALUES (:post_id, :user_id)");
        $stmt->execute(['post_id' => $post_id, 'user_id' => $user_id]);
        $liked = true;
    }

    // Get new count
    $countStmt = $pdo->prepare("SELECT COUNT(*) FROM tbl_likes WHERE post_id = :post_id");
    $countStmt->execute(['post_id' => $post_id]);
    $newCount = $countStmt->fetchColumn();

    echo json_encode(['success' => true, 'liked' => $liked, 'new_count' => $newCount]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Database error']);
}
?>
