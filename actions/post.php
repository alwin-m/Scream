<?php
session_start();
require_once '../includes/db.php';

if ($_SERVER['REQUEST_METHOD'] == 'POST' && isset($_POST['post_content'])) {
    // Check if user is logged in
    if (!isset($_SESSION['user_id'])) {
        // For demo purposes, if no user, we might die or redirect.
        // Assuming user_id 1 is admin/default if not set for testing, OR redirect to login.
        // die("Please login to scream.");
        // Redirect to login (assuming standard path)
        header("Location: ../login.php"); 
        exit();
    }

    $user_id = $_SESSION['user_id'];
    $post_content = mysqli_real_escape_string($con, $_POST['post_content']);
    $date = date("Y-m-d H:i:s");

    if (!empty($post_content)) {
        $query = "INSERT INTO tbl_post (user_id, post, date) VALUES ('$user_id', '$post_content', '$date')";
        if (mysqli_query($con, $query)) {
            // Success
            header("Location: ../index.php");
        } else {
            echo "Error: " . $query . "<br>" . mysqli_error($con);
        }
    } else {
        header("Location: ../index.php?error=empty");
    }
}
?>
