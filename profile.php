<?php
session_start();
require_once 'includes/db.php';

if (!isset($_SESSION['user_id'])) {
    header("Location: login.php");
    exit();
}

// Fetch current user details
$user_id = $_SESSION['user_id'];
$query = "SELECT * FROM tbluser WHERE user_id = '$user_id'";
$result = mysqli_query($con, $query);
$user = mysqli_fetch_assoc($result);

if (!$user) {
    die("User not found.");
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Profile - Scream</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="css/index.css">
    <style>
        .profile-header {
            text-align: center;
            padding: 3rem;
            background: linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,255,136,0.05) 100%);
            border-bottom: 1px solid var(--glass-border);
        }
        .profile-stats {
            display: flex;
            justify-content: center;
            gap: 2rem;
            margin-top: 1rem;
        }
        .stat-box {
            text-align: center;
        }
        .stat-value {
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--primary-color);
        }
    </style>
</head>
<body>
    <header class="header glass-panel">
        <h1>Scream.</h1>
        <nav>
            <a href="index.php">Home</a>
            <a href="actions/logout.php">Logout</a>
        </nav>
    </header>

    <div class="user-profile">
        <div class="profile-header">
            <div style="font-size: 5rem;">👤</div>
            <h2 style="font-size: 2.5rem; margin: 1rem 0;"><?php echo htmlspecialchars($user['username']); ?></h2>
            <div class="profile-stats">
                <div class="stat-box">
                    <div class="stat-value"><?php echo $user['age'] ?? 'N/A'; ?></div>
                    <div class="stat-label">Age</div>
                </div>
                <div class="stat-box">
                    <div class="stat-value"><?php echo $user['sex'] ?? 'N/A'; ?></div>
                    <div class="stat-label">Sex</div>
                </div>
            </div>
            <button class="btn" style="margin-top: 2rem; padding: 10px 20px; background: var(--glass-border); color: #fff; border: 1px solid #fff; border-radius: 20px; cursor: pointer;">Edit Profile</button>
        </div>
    </div>
    
    <div class="container">
        <h3>My Screams</h3>
        <!-- User's posts could go here -->
    </div>
</body>
</html>
