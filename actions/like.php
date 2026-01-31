<?php
session_start();
require_once '../includes/db.php';

header('Content-Type: application/json');

if (!isset($_SESSION['user_id'])) {
    echo json_encode(['success' => false, 'message' => 'Not logged in']);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] == 'POST' && isset($_POST['post_id'])) {
    $user_id = $_SESSION['user_id'];
    $post_id = intval($_POST['post_id']);

    // Check if already liked
    $check_query = "SELECT * FROM tbl_like WHERE post_id = '$post_id' AND user_id = '$user_id'";
    $check_result = mysqli_query($con, $check_query);

    if (mysqli_num_rows($check_result) > 0) {
        // Unlike
        $delete_query = "DELETE FROM tbl_like WHERE post_id = '$post_id' AND user_id = '$user_id'";
        mysqli_query($con, $delete_query);
    } else {
        // Like (Assuming thumbsup is just a flag, usually 1)
        $insert_query = "INSERT INTO tbl_like (user_id, post_id, thumbsup) VALUES ('$user_id', '$post_id', 1)";
        mysqli_query($con, $insert_query);
    }

    // Get new count
    $count_query = "SELECT COUNT(*) as likes FROM tbl_like WHERE post_id = '$post_id'";
    $count_res = mysqli_query($con, $count_query);
    $data = mysqli_fetch_assoc($count_res);
    
    echo json_encode(['success' => true, 'new_count' => $data['likes']]);
} else {
    echo json_encode(['success' => false, 'message' => 'Invalid request']);
}
?>
