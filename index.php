<?php
session_start();
// Include database connection
require_once 'includes/db.php';
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Scream - Home</title>
    <link rel="icon" type="image/png" href="assets/blue.webp">
    <!-- Main CSS -->
    <link rel="stylesheet" href="css/index.css">
</head>
<body>
    <header class="header">
        <h1>Scream</h1>
        <nav>
            <a href="#" class="profile-link" onclick="window.location='profile.php'; return false;">Profile</a>
            <a href="actions/logout.php">Logout</a>
            <a href="about.html">About</a>
        </nav>
    </header>

    <div class="container">
        <!-- Post Form -->
        <div class="post-box">
            <form action="actions/post.php" method="POST" id="postForm">
                <textarea name="post_content" placeholder="What's on your mind?" maxlength="1000" required></textarea>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 5px;">
                    <p id="charCount" style="margin: 0; color: #666;">0/1000</p>
                    <button type="submit">Post</button>
                </div>
            </form>
        </div>

        <!-- Display Posts -->
        <div id="posts-feed">
        <?php
        $query = "
            SELECT tbluser.username, tbl_post.post, tbl_post.post_id, tbl_post.date,
                   COUNT(tbl_like.thumbsup) AS likes 
            FROM tbl_post 
            JOIN tbluser ON tbl_post.user_id = tbluser.user_id
            LEFT JOIN tbl_like ON tbl_post.post_id = tbl_like.post_id
            GROUP BY tbl_post.post_id, tbluser.username, tbl_post.post, tbl_post.date
            ORDER BY tbl_post.date DESC 
            LIMIT 50";
        
        $result = mysqli_query($con, $query);

        if ($result && mysqli_num_rows($result) > 0) {
            $delay_index = 0;
            while ($post = mysqli_fetch_assoc($result)) {
                $username = htmlspecialchars($post['username']);
                $content = htmlspecialchars($post['post']);
                $likes = $post['likes'];
                $postId = $post['post_id'];
                
                // Add staggered animation delay inline or via JS
                // We'll trust JS to handle the refined staggering, but initial CSS has post:nth-child rules too.
                 
                echo "<div class='post'>";
                echo "<h3 class='username' data-username='$username' style='margin-top:0;'><span style='margin-right:8px;'>👤</span> $username</h3>";
                echo "<p>$content</p>";
                echo "<div class='actions'>";
                // Like button
                echo "<button class='like-btn' data-post-id='$postId'>👍 <span class='like-count-text'>$likes</span></button>";
                echo "</div>";
                echo "</div>";
                $delay_index++;
            }
        } else {
            echo "<div class='post'><p>No posts yet. Be the first to post something!</p></div>";
        }
        ?>
        </div>
    </div>

    <!-- Modals -->
    <!-- Likes List Modal -->
    <div class="modal" id="likesModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('likesModal')">&times;</span>
            <h3>Liked By</h3>
            <ul id="likesList" style="list-style: none; padding: 0; margin-top: 1rem;"></ul>
        </div>
    </div>

    <!-- Profile Modal -->
    <div class="modal" id="profileModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('profileModal')">&times;</span>
            <h3>User Profile</h3>
            <p><strong>Username:</strong> <span id="profileUsername"></span></p>
            <p><strong>Followers:</strong> <span id="profilefollow"></span></p>
            <p><strong>Age:</strong> <span id="profileAge"></span></p>
            <p><strong>Sex:</strong> <span id="profileSex"></span></p>
            <button id="followBtn" class="follow-btn" style="background-color: #4CAF50; color: white; padding: 10px 20px; border: none; border-radius: 5px; cursor: pointer; margin-top:10px; width:100%;">Follow</button>
        </div>
    </div>

    <!-- Search Modal -->
    <div class="modal" id="searchModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('searchModal')">&times;</span>
            <h3>Search Users</h3>
            <input type="text" id="searchInput" placeholder="Type a username..." autocomplete="off"/>
            <button id="searchSubmit">Search</button>
            <div id="searchResults" class="search-results"></div>
        </div>
    </div>

    <!-- Footer -->
    <footer class="footer">
        &copy; <?php echo date("Y"); ?> Scream. All rights reserved.
        <button id="searchButton" class="search-btn">🔍</button>
    </footer>

    <script src="js/index.js"></script>
</body>
</html>
<?php
mysqli_close($con);
?>
