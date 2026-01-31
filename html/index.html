<?php
session_start();
// Database connection
$con = mysqli_connect("localhost", "root", "password", "ms");

if (!$con) {
    die("Connection failed: " . mysqli_connect_error());
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Scream - Home</title>
    <link rel="icon" type="image/png" href="blue.webp">  <!-- Favicon added here -->
    <style>
        /* General Styles */
        body {
            margin: 0;
            padding: 0;
            font-family: 'Arial', sans-serif;
            background: url('image.jpg') no-repeat center center fixed;
            background-size: cover;
            color: #333;
        }

        .header {
            background-color: rgba(0, 0, 0, 0.8);
            color: #fff;
            padding: 15px 40px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 1000;
            backdrop-filter: blur(10px);
            animation: scaleHeader 0.5s ease-out;
            transition: all 0.3s ease;
        }

        .header.scrolled {
            transform: translateY(-10px);
            background-color: rgba(0, 0, 0, 0.9);
        }

        .header h1 {
            font-size: 28px;
            margin: 0;
            animation: fadeIn 1s ease-in-out;
        }

        .header nav {
            display: flex;
            gap: 20px;
        }

        .header nav a {
            color: #fff;
            text-decoration: none;
            font-weight: bold;
            font-size: 16px;
            transition: color 0.3s ease, transform 0.3s ease;
        }

        .header nav a:hover {
            color: #4CAF50;
            transform: translateY(-2px);
        }

        .container {
            display: flex;
            flex-direction: column;
            max-width: 1200px;
            margin: 40px auto;
            padding: 30px;
            background-color: rgba(255, 255, 255, 0.95);
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.2);
            border-radius: 12px;
            animation: slideUp 0.5s ease-in-out;
        }

        .post-box {
            margin-bottom: 30px;
        }

        .post-box textarea {
            width: 100%;
            height: 120px;
            padding: 15px;
            font-size: 18px;
            border: 1px solid #888;
            border-radius: 8px;
            box-sizing: border-box;
            resize: none;
            transition: border-color 0.3s ease;
        }

        .post-box textarea:focus {
            border-color: #4CAF50;
        }

        .post-box button {
            margin-top: 15px;
            padding: 12px 25px;
            background-color: #28a745;
            color: #fff;
            border: none;
            border-radius: 8px;
            font-size: 18px;
            cursor: pointer;
            transition: background-color 0.3s ease, transform 0.3s ease;
        }

        .post-box button:hover {
            background-color: #218838;
            transform: translateY(-2px);
        }

        .post {
            margin-bottom: 25px;
            padding: 20px;
            background-color: #ffffff;
            border: 1px solid #ccc;
            border-radius: 12px;
            transition: box-shadow 0.3s ease, transform 0.3s ease;
            animation: slideInRight 0.5s ease-out forwards;
            opacity: 0;
        }

        .post:nth-child(1) { animation-delay: 0.1s; }
        .post:nth-child(2) { animation-delay: 0.2s; }
        .post:nth-child(3) { animation-delay: 0.3s; }

        .post:hover {
            box-shadow: 0 6px 12px rgba(0, 0, 0, 0.2);
            transform: translateY(-3px);
        }

        .post h3 {
            margin: 0 0 15px 0;
            font-size: 20px;
            color: #333;
            cursor: pointer;
            transition: color 0.3s ease;
        }

        .post h3:hover {
            color: #4CAF50;
        }

        .post p {
            margin: 0;
            font-size: 18px;
            color: #555;
        }

        .actions {
            margin-top: 15px;
        }

        .actions button {
            padding: 8px 15px;
            font-size: 24px;
            background: none;
            border: none;
            cursor: pointer;
            transition: transform 0.3s ease;
        }

        .actions button:hover {
            transform: scale(1.2);
        }

        .footer {
            text-align: center;
            padding: 20px;
            background-color: rgba(0, 0, 0, 0.8);
            color: #fff;
            position: fixed;
            bottom: 0;
            width: 100%;
            backdrop-filter: blur(10px);
            animation: slideUp 0.5s ease-out 0.2s forwards;
            transform: translateY(100%);
        }

        /* Modal Styles */
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0, 0, 0, 0.5);
            justify-content: center;
            align-items: center;
            animation: fadeIn 0.3s ease-in-out;
        }

        .modal-content {
            background-color: #fff;
            padding: 30px;
            border-radius: 12px;
            max-width: 500px;
            width: 100%;
            position: relative;
            animation: bounceIn 0.4s ease-out;
        }

        .close-btn {
            position: absolute;
            top: 15px;
            right: 15px;
            font-size: 24px;
            cursor: pointer;
            transition: color 0.3s ease;
        }

        .close-btn:hover {
            color: #4CAF50;
        }

        /* Search Modal Styles */
        .search-btn {
            background: none;
            border: none;
            font-size: 28px;
            cursor: pointer;
            color: #fff;
            margin-left: 20px;
            transition: transform 0.3s ease;
        }

        .search-btn:hover {
            transform: scale(1.2);
        }

        .search-results {
            max-height: 300px;
            overflow-y: auto;
            margin-top: 15px;
            border-top: 1px solid #ccc;
            padding-top: 15px;
        }

        .search-results div {
            padding: 12px;
            border-bottom: 1px solid #eee;
            cursor: pointer;
            transition: background 0.3s ease;
            animation: fadeIn 0.3s ease forwards;
            opacity: 0;
        }

        .search-results div:hover {
            background: #f0f0f0;
        }

        #searchInput {
            width: 100%;
            padding: 12px;
            border: 1px solid #ccc;
            border-radius: 8px;
            font-size: 18px;
            margin-bottom: 15px;
            transition: border-color 0.3s ease;
        }

        #searchInput:focus {
            border-color: #4CAF50;
        }

        #searchSubmit {
            width: 100%;
            padding: 12px;
            background-color: #4CAF50;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 18px;
            transition: background 0.3s ease, transform 0.3s ease;
        }

        #searchSubmit:hover {
            background-color: #45a049;
            transform: translateY(-2px);
        }

        /* Animations */
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        @keyframes slideUp {
            from { transform: translateY(20px); opacity: 0; }
            to { transform: translateY(0); opacity: 1; }
        }

        @keyframes bounce {
            0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
            40% { transform: translateY(-10px); }
            60% { transform: translateY(-5px); }
        }

        @keyframes pulse {
            0% { transform: scale(1); }
            50% { transform: scale(1.1); }
            100% { transform: scale(1); }
        }

        @keyframes slideInRight {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }

        @keyframes scaleHeader {
            from { transform: scale(0.95); opacity: 0; }
            to { transform: scale(1); opacity: 1; }
        }

        @keyframes bounceIn {
            0% { transform: scale(0.5); opacity: 0; }
            60% { transform: scale(1.1); opacity: 1; }
            100% { transform: scale(1); }
        }
    </style>
</head>
<body>
    <header class="header">
        <h1>Scream</h1>
        <nav>
            <a href="#" onclick="window.location='profile.php'; return false;">Profile</a>
            <a href="long.php">Logout</a>
            <a href="about.html">About</a>
        </nav>
    </header>

    <div class="container">
        <!-- Post Form -->
        <div class="post-box">
            <form action="post.php" method="POST">
                <textarea name="post_content" placeholder="What's on your mind?" maxlength="1000" required></textarea>
                <p id="charCount">0/1000</p>
                <button type="submit">Post</button>
            </form>
        </div>

        <!-- Display Posts -->
        <?php
        $query = "
            SELECT tbluser.username, tbl_post.post, tbl_post.post_id, 
                   COUNT(tbl_like.thumbsup) AS likes 
            FROM tbl_post 
            JOIN tbluser ON tbl_post.user_id = tbluser.user_id
            LEFT JOIN tbl_like ON tbl_post.post_id = tbl_like.post_id
            GROUP BY tbl_post.post_id, tbluser.username, tbl_post.post
            ORDER BY tbl_post.date DESC 
            LIMIT 10";
        $result = mysqli_query($con, $query);

        if (mysqli_num_rows($result) > 0) {
            while ($post = mysqli_fetch_assoc($result)) {
                echo "<div class='post'>";
                echo "<h3 class='username' data-username='" . htmlspecialchars($post['username']) . "'><span class='icon'>👤&nbsp;&nbsp;" . htmlspecialchars($post['username']) . "</span></h3>";
                echo "<p>" . htmlspecialchars($post['post']) . "</p>";
                echo "<div class='actions'>";
                echo "<a href='like.php?post_id=" . $post['post_id'] . "'>";
                echo "<button class='like-btn'>👍 <span class='like-count' data-post-id='" . $post['post_id'] . "'>" . htmlspecialchars($post['likes']) . "</span></button>";
                echo "</a>";
                echo "</div>";
                echo "</div>";
            }
        } else {
            echo "<p>No posts yet. Be the first to post something!</p>";
        }
        ?>
    </div>

    <!-- Modals -->
    <div class="modal" id="likesModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('likesModal')">&times;</span>
            <h3>Liked By</h3>
            <ul id="likesList"></ul>
        </div>
    </div>

    <div class="modal" id="profileModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('profileModal')">&times;</span>
            <h3>User Profile</h3>
            <p><strong>Username:</strong> <span id="profileUsername"></span></p>
            <p><strong>follow:</strong> <span id="profilefollow"></span></p>
            <p><strong>Age:</strong> <span id="profileAge"></span></p>
            <p><strong>Sex:</strong> <span id="profileSex"></span></p>
            <button id="followBtn" class="follow-btn" onclick="toggleFollow()">Follow</button>
        </div>
    </div>

    <!-- Search Modal -->
    <div class="modal" id="searchModal">
        <div class="modal-content">
            <span class="close-btn" onclick="closeModal('searchModal')">&times;</span>
            <h3>Search Users</h3>
            <input type="text" id="searchInput" placeholder="Type a letter and press Search" />
            <button id="searchSubmit">Search</button>
            <div id="searchResults" class="search-results"></div>
        </div>
    </div>

    <footer class="footer">
        &copy; 2025 Scream. All rights reserved.
        <button id="searchButton" class="search-btn">🔍</button>
    </footer>

    <script>
        // Update character count dynamically
        const textarea = document.querySelector('textarea[name="post_content"]');
        const charCount = document.getElementById('charCount');

        textarea.addEventListener('input', () => {
            charCount.textContent = `${textarea.value.length}/1000`;
        });

        // Modal functionality for likes
        const likesModal = document.getElementById('likesModal');
        const likesList = document.getElementById('likesList');

        document.querySelectorAll('.like-count').forEach(likeCount => {
            likeCount.addEventListener('click', () => {
                const postId = likeCount.getAttribute('data-post-id');
                fetchLikes(postId);
                openModal('likesModal');
            });
        });

        // Open Search Modal
        document.getElementById('searchButton').addEventListener('click', () => {
            openModal('searchModal');
        });

        // Handle Search Submission
        document.getElementById('searchSubmit').addEventListener('click', () => {
            const searchLetter = document.getElementById('searchInput').value.trim();
            if (searchLetter.length === 1) {
                fetchSearchResults(searchLetter);
            } else {
                alert('Please enter a single letter.');
            }
        });

        // Fetch Search Results
        function fetchSearchResults(letter) {
            fetch(`search_users.php?letter=${letter}`)
                .then(response => response.json())
                .then(data => {
                    const resultsContainer = document.getElementById('searchResults');
                    resultsContainer.innerHTML = '';
                    if (data.length > 0) {
                        data.forEach(user => {
                            const userDiv = document.createElement('div');
                            userDiv.textContent = user.username;
                            userDiv.addEventListener('click', () => {
                                window.location.href = `profile.php?username=${user.username}`;
                            });
                            resultsContainer.appendChild(userDiv);
                        });
                    } else {
                        resultsContainer.innerHTML = '<div>No users found.</div>';
                    }
                })
                .catch(error => console.error('Error:', error));
        }

        // Open and Close Modals
        function openModal(modalId) {
            document.getElementById(modalId).style.display = 'flex';
        }

        function closeModal(modalId) {
            document.getElementById(modalId).style.display = 'none';
        }

        // Close modal when clicking outside of it
        window.addEventListener('click', (event) => {
            if (event.target.classList.contains('modal')) {
                closeModal(event.target.id);
            }
        });

        // Header scroll animation
        window.addEventListener('scroll', () => {
            const header = document.querySelector('.header');
            header.classList.toggle('scrolled', window.scrollY > 50);
        });

        // Animate posts on load
        document.querySelectorAll('.post').forEach((post, index) => {
            post.style.animationDelay = `${index * 0.1}s`;
            post.style.opacity = 1;
        });

        // Add pulse animation to new posts
        function addNewPostAnimation(postElement) {
            postElement.classList.add('new-post');
            setTimeout(() => {
                postElement.classList.remove('new-post');
            }, 1000);
        }

        // Enhanced like button click animation
        document.querySelectorAll('.like-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                e.target.style.transform = 'scale(1.5)';
                setTimeout(() => {
                    e.target.style.transform = 'scale(1)';
                }, 300);
            });
        });

        // Animate search results items
        function animateSearchResults(items) {
            items.forEach((item, index) => {
                item.style.animationDelay = `${index * 0.1}s`;
                item.style.opacity = 1;
            });
        }
    </script>
</body>
</html>
<?php
mysqli_close($con);
?>
