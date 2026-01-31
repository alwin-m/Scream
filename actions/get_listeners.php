<?php
require_once '../includes/db.php';
header('Content-Type: application/json');

if (isset($_GET['post_id'])) {
    $post_id = intval($_GET['post_id']);
    
    $query = "
        SELECT tbluser.username 
        FROM tbl_like 
        JOIN tbluser ON tbl_like.user_id = tbluser.user_id 
        WHERE tbl_like.post_id = '$post_id'
    ";
    
    $result = mysqli_query($con, $query);
    $users = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $users[] = ['username' => $row['username']];
    }
    echo json_encode($users);
}
?>
