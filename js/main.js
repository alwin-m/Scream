document.addEventListener('DOMContentLoaded', () => {
    // 1. Auth Check on Load
    let currentUser = null;

    fetch('../actions/check_auth.php')
        .then(res => res.json())
        .then(data => {
            if (!data.authenticated) {
                // If on index.html and not logged in, redirect
                window.location.href = '../html/login.html';
            } else {
                currentUser = data.user;
                updateUserProfile(currentUser);
            }
        })
        .catch(err => console.error("Auth check failed", err));

    function updateUserProfile(user) {
        // Update sidebar or profile elements if they exist
        // For now, mostly used for posting
    }

    // 2. Navigation
    const navLinks = document.querySelectorAll('.nav-links a');
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            // e.preventDefault(); // allow default for now unless SPA
            navLinks.forEach(l => l.classList.remove('active'));
            link.classList.add('active');
        });
    });

    // 3. Post Creation (AJAX)
    const submitBtn = document.getElementById('submit-post');
    const postInput = document.getElementById('post-input');
    const postsContainer = document.getElementById('posts-stream');

    submitBtn.addEventListener('click', async () => {
        const content = postInput.value.trim();
        if (!content) return;

        // Disable button
        submitBtn.disabled = true;
        submitBtn.textContent = 'Posting...';

        try {
            const res = await fetch('../actions/create_post.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            });

            const result = await res.json();

            if (res.ok && result.success) {
                // Determine display name
                const displayUser = currentUser ? currentUser.username : 'Me';
                const now = new Date();
                const timeString = 'Just now';

                // Create post element locally (Optimistic UI)
                const newPost = document.createElement('article');
                newPost.className = 'post-card glass-panel';
                newPost.style.animation = 'fadeIn 0.5s ease';

                newPost.innerHTML = `
                    <div class="post-header">
                        <div class="user-info">
                            <img src="https://ui-avatars.com/api/?name=${displayUser}&background=random" alt="Avatar" class="avatar-sm">
                            <div>
                                <h3 class="username">${displayUser} <span class="handle">@${displayUser}</span></h3>
                                <span class="timestamp">${timeString}</span>
                            </div>
                        </div>
                        <button class="more-options"><i class="fa-solid fa-ellipsis"></i></button>
                    </div>
                    <div class="post-content">
                        <p>${escapeHtml(content)}</p>
                    </div>
                    <div class="post-footer">
                        <button class="action-btn"><i class="fa-regular fa-heart"></i> <span>0</span></button>
                        <button class="action-btn"><i class="fa-regular fa-comment"></i> <span>0</span></button>
                        <button class="action-btn"><i class="fa-solid fa-share-nodes"></i></button>
                    </div>
                `;

                postsContainer.insertBefore(newPost, postsContainer.firstChild);
                postInput.value = '';
            } else {
                alert('Failed to post: ' + (result.error || 'Unknown error'));
            }
        } catch (err) {
            console.error(err);
            alert('Error connecting to server.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Post';
        }
    });

    // 4. Like Interactions (Frontend only for now)
    postsContainer.addEventListener('click', (e) => {
        const btn = e.target.closest('.action-btn');
        if (!btn) return;

        const icon = btn.querySelector('i');
        const countSpan = btn.querySelector('span');

        if (icon.classList.contains('fa-heart')) {
            if (icon.classList.contains('fa-regular')) {
                icon.classList.remove('fa-regular');
                icon.classList.add('fa-solid');
                icon.style.color = '#e25555';
                if (countSpan) countSpan.textContent = parseInt(countSpan.textContent) + 1;
            } else {
                icon.classList.remove('fa-solid');
                icon.classList.add('fa-regular');
                icon.style.color = 'var(--text-secondary)';
                if (countSpan) countSpan.textContent = parseInt(countSpan.textContent) - 1;
            }
        }
    });

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});
