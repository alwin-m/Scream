<?php
// actions/fetch_posts.php
require_once '../backend/db.php';
session_start();

header('Content-Type: application/json');

$current_user_id = $_SESSION['user_id'] ?? 0;

try {
    // Fetch posts with user info, like count, and whether current user liked it
    $sql = "
        SELECT 
            p.id, 
            p.content, 
            p.created_at, 
            u.username, 
            COUNT(l.id) as like_count,
            MAX(CASE WHEN l.user_id = :current_user_id THEN 1 ELSE 0 END) as is_liked
        FROM tbl_posts p
        JOIN tbl_users u ON p.user_id = u.id
        LEFT JOIN tbl_likes l ON p.id = l.post_id
        WHERE p.is_deleted = 0
        GROUP BY p.id
        ORDER BY p.created_at DESC
    ";

    $stmt = $pdo->prepare($sql);
    $stmt->execute(['current_user_id' => $current_user_id]);
    $posts = $stmt->fetchAll();

    echo json_encode(['success' => true, 'posts' => $posts]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Database error: ' . $e->getMessage()]);
}
?>
