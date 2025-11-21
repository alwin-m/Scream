<?php
// signup_action.php
// Connects signup form to database and inserts new user

// Database connection settings
$host = 'localhost';
$db   = 'scream';
$user = 'root';
$pass = 'scream';

$conn = new mysqli($host, $user, $pass, $db);

if ($conn->connect_error) {
    die('Connection failed: ' . $conn->connect_error);
}

// Get form data safely
$username = isset($_POST['username']) ? trim($_POST['username']) : '';
$email    = isset($_POST['email']) ? trim($_POST['email']) : '';
$password = isset($_POST['password']) ? $_POST['password'] : '';
$dob      = isset($_POST['dob']) ? $_POST['dob'] : null;

// Basic validation
if ($username === '' || $email === '' || $password === '') {
    die('All fields are required.');
}

// Hash password
$password_hash = password_hash($password, PASSWORD_DEFAULT);

// Prepare SQL insert
$stmt = $conn->prepare("INSERT INTO tbl_users (username, email, password_hash, dob, created_at) VALUES (?, ?, ?, ?, NOW())");
$stmt->bind_param("ssss", $username, $email, $password_hash, $dob);

if ($stmt->execute()) {
    echo "Signup successful!";
} else {
    echo "Error: " . $stmt->error;
}

$stmt->close();
$conn->close();
?>
