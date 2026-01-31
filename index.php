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
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
    <!-- Main CSS -->
    <link rel="stylesheet" href="css/index.css">
</head>
<body>
    <header class="header glass-panel">
        <h1>Scream.</h1>
        <nav>
            <a href="profile.php" id="profileLink">Profile</a>
            <a href="actions/logout.php" id="logoutLink">Logout</a>
            <a href="about.html">About</a>
            <a href="#" id="searchButton" style="font-size: 1.2rem;">🔍</a>
        </nav>
    </header>

    <div class="container">
        <!-- Post Form -->
        <div class="post-box glass-panel">
            <form action="actions/post.php" method="POST" id="postForm">
                <textarea name="post_content" placeholder="What's screaming in your mind?" maxlength="1000" required></textarea>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <p id="charCount">0/1000</p>
                    <button type="submit">Scream It</button>
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
            while ($post = mysqli_fetch_assoc($result)) {
                $username = htmlspecialchars($post['username']);
                $content = htmlspecialchars($post['post']);
                $likes = $post['likes'];
                $postId = $post['post_id'];
                
                echo "<div class='post glass-panel'>";
                echo "<h3 class='username' data-username='$username'><div class='icon'>👤</div> <span class='username-text'>$username</span></h3>";
                echo "<p>$content</p>";
                echo "<div class='actions'>";
                // Like button using AJAX logic defined in js/index.js
                echo "<button class='like-btn' data-post-id='$postId'>👍 <span class='like-count-text'>$likes</span></button>";
                echo "</div>";
                echo "</div>";
            }
        } else {
            echo "<div class='post glass-panel'><p>No screams yet. Be the first to shout into the void!</p></div>";
        }
        ?>
        </div>
    </div>

    <!-- Footer -->
    <footer class="footer">
        &copy; <?php echo date("Y"); ?> Scream. Quietly Loud.
    </footer>

    <!-- Modals -->
    <!-- Likes List Modal -->
    <div class="modal" id="likesModal">
        <div class="modal-content glass-panel">
            <span class="close-btn" onclick="closeModal('likesModal')">&times;</span>
            <h3>Liked By</h3>
            <ul id="likesList" style="list-style: none; padding: 0; margin-top: 1rem;"></ul>
        </div>
    </div>

    <!-- Profile Modal -->
    <div class="modal" id="profileModal">
        <div class="modal-content glass-panel">
            <span class="close-btn" onclick="closeModal('profileModal')">&times;</span>
            <div style="text-align: center;">
                <div style="font-size: 4rem; margin-bottom: 1rem;">👤</div>
                <h3 id="profileUsername" style="font-size: 1.5rem; margin-bottom: 0.5rem;"></h3>
                <p>Followers: <span id="profilefollow" style="color: var(--primary-color); font-weight: bold;">0</span></p>
                <div style="margin-top: 1rem; color: var(--text-muted); text-align: left;">
                    <p><strong>Age:</strong> <span id="profileAge">N/A</span></p>
                    <p><strong>Sex:</strong> <span id="profileSex">N/A</span></p>
                </div>
                <button id="followBtn" style="margin-top: 1.5rem; width: 100%; padding: 0.8rem; border-radius: 8px; border: none; background: var(--primary-color); cursor: pointer; font-weight: bold;">Follow</button>
            </div>
        </div>
    </div>

    <!-- Search Modal -->
    <div class="modal" id="searchModal">
        <div class="modal-content glass-panel">
            <span class="close-btn" onclick="closeModal('searchModal')">&times;</span>
            <h3>Search Users</h3>
            <input type="text" id="searchInput" placeholder="Type a username..." autocomplete="off"/>
            <button id="searchSubmit">Search</button>
            <div id="searchResults" class="search-results"></div>
        </div>
    </div>

    <script src="js/index.js"></script>
    <script>
        // Inline pass for any PHP session data if needed
    </script>
</body>
</html>
<?php
mysqli_close($con);
?>
