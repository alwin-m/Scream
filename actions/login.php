<?php
// actions/login.php
require_once '../backend/db.php';

session_start();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email = trim($_POST['email']);
    $password = $_POST['password'];

    if (empty($email) || empty($password)) {
        die("Please fill all fields.");
    }

    try {
        $stmt = $pdo->prepare("SELECT id, username, password_hash FROM tbl_users WHERE email = :email");
        $stmt->execute(['email' => $email]);
        $user = $stmt->fetch();

        if ($user && password_verify($password, $user['password_hash'])) {
            // Login Success
            $_SESSION['user_id'] = $user['id'];
            $_SESSION['username'] = $user['username'];
            
            // Updates last login
            $update = $pdo->prepare("UPDATE tbl_users SET last_login = NOW() WHERE id = :id");
            $update->execute(['id' => $user['id']]);

            header("Location: ../html/index.html");
            exit;
        } else {
            die("Invalid email or password.");
        }
    } catch (PDOException $e) {
        die("Error: " . $e->getMessage());
    }
}
?>
