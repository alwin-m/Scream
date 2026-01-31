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
    <!-- Main CSS -->
    <link rel="stylesheet" href="css/index.css">
</head>
<body>
    <header class="header">
        <h1>Scream</h1>
        <nav>
            <a href="index.php">Home</a>
            <a href="actions/logout.php">Logout</a>
        </nav>
    </header>

    <div class="user-profile" style="background-color: #fff; border-bottom: 1px solid #ddd;">
        <div class="profile-header" style="text-align: center; padding: 40px; max-width: 800px; margin: 0 auto;">
            <div style="font-size: 5rem; margin-bottom: 20px;">👤</div>
            <h2 style="font-size: 2.5rem; margin: 0; color: #333;"><?php echo htmlspecialchars($user['username']); ?></h2>
            <div class="profile-stats">
                <div class="stat-box">
                    <div class="stat-value"><?php echo $user['age'] ?? 'N/A'; ?></div>
                    <div class="stat-label">Age</div>
                </div>
                <div class="stat-box">
                    <div class="stat-value"><?php echo $user['sex'] ?? 'N/A'; ?></div>
                    <div class="stat-label">Sex</div>
                </div>
                <!-- Followers could be added here if DB supports it -->
            </div>
            <button class="btn" style="margin-top: 30px; padding: 12px 30px; background-color: #4CAF50; color: #fff; border: none; border-radius: 8px; font-size: 16px; cursor: pointer;">Edit Profile</button>
        </div>
    </div>
    
    <div class="container">
        <h3 style="border-bottom: 2px solid #4CAF50; display: inline-block; padding-bottom: 5px; margin-bottom: 20px;">My Screams</h3>
        <!-- User's posts logic could replicate index.php loop but filtered by user_id -->
        <?php
             $u_id = $user['user_id'];
             $query = "SELECT * FROM tbl_post WHERE user_id = '$u_id' ORDER BY date DESC";
             $result = mysqli_query($con, $query);
             if ($result && mysqli_num_rows($result) > 0) {
                 while($post = mysqli_fetch_assoc($result)) {
                     echo "<div class='post'>";
                     echo "<h3>👤 ".htmlspecialchars($user['username'])."</h3>";
                     echo "<p>".htmlspecialchars($post['post'])."</p>";
                     echo "</div>";
                 }
             } else {
                 echo "<p>You haven't screamed yet.</p>";
             }
        ?>
    </div>
</body>
</html>
