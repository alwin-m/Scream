<?php
session_start();
require_once 'config.php';

// Validate input
if (!isset($_POST['username'], $_POST['password'])) {
    die('Invalid request');
}

$username = trim($_POST['username']);
$password = trim($_POST['password']);

// Query user
$sql = "SELECT * FROM tbl_users WHERE username = ? LIMIT 1";
$stmt = $conn->prepare($sql);
$stmt->bind_param("s", $username);
$stmt->execute();
$result = $stmt->get_result();

if ($result->num_rows === 1) {
    $user = $result->fetch_assoc();

    // Verify password
    if (password_verify($password, $user['password_hash'])) {
        // Create session
        $_SESSION['user_id'] = $user['id'];
        $_SESSION['username'] = $user['username'];
        $_SESSION['logged_in'] = true;

        // Update last login time
        $update = $conn->prepare("UPDATE tbl_users SET last_login = NOW() WHERE id = ?");
        $update->bind_param("i", $user['id']);
        $update->execute();

        header("Location: index.php");
        exit();
    } else {
        header("Location: login.php?error=wrong_password");
        exit();
    }
} else {
    header("Location: login.php?error=user_not_found");
    exit();
}
?>