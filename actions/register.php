<?php
// actions/register.php
require_once '../backend/db.php';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim($_POST['username']);
    $email = trim($_POST['email']);
    $password = $_POST['password'];

    // Basic validation
    if (empty($username) || empty($email) || empty($password)) {
        die("Please fill all fields.");
    }

    // Hash password
    $password_hash = password_hash($password, PASSWORD_DEFAULT);

    try {
        $stmt = $pdo->prepare("INSERT INTO tbl_users (username, email, password_hash) VALUES (:username, :email, :password_hash)");
        $stmt->execute([
            'username' => $username, 
            'email' => $email, 
            'password_hash' => $password_hash
        ]);

        // Start session and log user in immediately
        session_start();
        $_SESSION['user_id'] = $pdo->lastInsertId();
        $_SESSION['username'] = $username;

        // Redirect to home
        header("Location: ../html/index.html");
        exit;
    } catch (PDOException $e) {
        if ($e->getCode() == 23000) {
            die("Username or Email already exists.");
        }
        die("Error: " . $e->getMessage());
    }
}
?>
