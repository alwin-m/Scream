<?php
// actions/register.php
require_once '../backend/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header("Location: ../html/signup.html");
    exit;
}

$username = strtolower(trim($_POST['username'] ?? ''));
$email    = strtolower(trim($_POST['email'] ?? ''));
$password = $_POST['password'] ?? '';

// Basic validation
if ($username === '' || $email === '' || $password === '') {
    die("All fields are required.");
}

if (strlen($username) < 3) {
    die("Username must be at least 3 characters.");
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    die("Invalid email address.");
}

if (strlen($password) < 6) {
    die("Password must be at least 6 characters.");
}

// Hash password
$password_hash = password_hash($password, PASSWORD_DEFAULT);

try {
    $stmt = $pdo->prepare(
        "INSERT INTO tbl_users (username, email, password_hash)
         VALUES (:username, :email, :password_hash)"
    );

    $stmt->execute([
        'username' => $username,
        'email' => $email,
        'password_hash' => $password_hash
    ]);

    // Auto-login after signup
    session_start();
    $_SESSION['user_id']  = $pdo->lastInsertId();
    $_SESSION['username'] = $username;

    header("Location: ../html/index.html");
    exit;

} catch (PDOException $e) {
    if ($e->getCode() == 23000) {
        die("Username or Email already exists.");
    }
    die("Something went wrong. Please try again.");
}
