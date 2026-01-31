// DOM Elements
const postTextarea = document.querySelector('textarea[name="post_content"]');
const charCount = document.getElementById('charCount');
const header = document.querySelector('.header');
const likesModal = document.getElementById('likesModal');
const profileModal = document.getElementById('profileModal');
const searchModal = document.getElementById('searchModal');
const searchInput = document.getElementById('searchInput');
const searchResults = document.getElementById('searchResults');

// Character Count
if (postTextarea) {
    postTextarea.addEventListener('input', () => {
        const currentLength = postTextarea.value.length;
        charCount.textContent = `${currentLength}/1000`;
        if (currentLength > 900) {
            charCount.style.color = '#ff4444';
        } else {
            charCount.style.color = '#666';
        }
    });
}

// Header Scroll Animation
window.addEventListener('scroll', () => {
    if (window.scrollY > 50) {
        header.classList.add('scrolled');
    } else {
        header.classList.remove('scrolled');
    }
});

// Modal Logic
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'flex';
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
}

// Close on outside click
window.addEventListener('click', (event) => {
    if (event.target.classList.contains('modal')) {
        closeModal(event.target.id);
    }
});

// Expose to global scope for HTML onclick
window.closeModal = closeModal;
window.openModal = openModal;

// Search Logic
const searchBtn = document.getElementById('searchButton');
if (searchBtn) {
    searchBtn.addEventListener('click', (e) => {
        e.preventDefault();
        openModal('searchModal');
    });
}

const searchSubmitBtn = document.getElementById('searchSubmit');
if (searchSubmitBtn) {
    searchSubmitBtn.addEventListener('click', () => {
        const query = searchInput.value.trim();
        if (query.length > 0) {
            fetchSearchResults(query);
        } else {
            alert('Please enter a search term.');
        }
    });
}

function fetchSearchResults(query) {
    fetch(`actions/search_users.php?q=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(data => {
            searchResults.innerHTML = '';
            if (data.length > 0) {
                // Animate results
                data.forEach((user, index) => {
                    const div = document.createElement('div');
                    div.innerHTML = `<strong>${user.username}</strong>`;
                    div.style.animationDelay = `${index * 0.1}s`; // Stagger
                    div.addEventListener('click', () => {
                        window.location.href = `profile.php?username=${encodeURIComponent(user.username)}`;
                    });
                    searchResults.appendChild(div);
                });
            } else {
                searchResults.innerHTML = '<div style="padding:1rem;">No users found.</div>';
            }
        })
        .catch(err => console.error('Search error:', err));
}

// Like System
document.querySelectorAll('.like-btn').forEach(btn => {
    btn.addEventListener('click', function (e) {
        // Prevent default if it's inside an anchor (though we use buttons now)
        e.preventDefault();

        // Animation
        this.style.transform = 'scale(1.5)';
        setTimeout(() => {
            this.style.transform = 'scale(1)';
        }, 300);

        const postId = this.getAttribute('data-post-id');
        toggleLike(postId, this);
    });
});

function toggleLike(postId, btnElement) {
    fetch('actions/like.php', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: `post_id=${postId}`
    })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                const countSpan = btnElement.querySelector('.like-count-text');
                if (countSpan) countSpan.textContent = data.new_count;
            }
        })
        .catch(err => console.error('Like error:', err));
}

document.querySelectorAll('.like-count-text').forEach(span => {
    span.addEventListener('click', (e) => {
        e.stopPropagation();
        const btn = span.closest('.like-btn');
        const postId = btn.getAttribute('data-post-id');
        fetchLikers(postId);
    });
});

function fetchLikers(postId) {
    openModal('likesModal');
    const list = document.getElementById('likesList');
    list.innerHTML = '<li>Loading...</li>';

    fetch(`actions/get_listeners.php?post_id=${postId}`)
        .then(res => res.json())
        .then(users => {
            list.innerHTML = '';
            if (users.length === 0) {
                list.innerHTML = '<li>No likes yet.</li>';
                return;
            }
            users.forEach(u => {
                const li = document.createElement('li');
                li.textContent = u.username;
                li.style.padding = '10px';
                li.style.borderBottom = '1px solid #eee';
                list.appendChild(li);
            });
        });
}

// Profile Modal specific logic for the main page (if clicking typical usernames inside posts)
document.querySelectorAll('.username').forEach(userElem => {
    userElem.addEventListener('click', () => {
        const username = userElem.getAttribute('data-username');
        openProfileModal(username);
    });
});

function openProfileModal(username) {
    openModal('profileModal');
    document.getElementById('profileUsername').textContent = username;

    // Reset
    document.getElementById('profileAge').textContent = '...';
    document.getElementById('profileSex').textContent = '...';
    document.getElementById('profilefollow').textContent = '...';

    fetch(`actions/get_user.php?username=${encodeURIComponent(username)}`)
        .then(res => res.json())
        .then(data => {
            document.getElementById('profileAge').textContent = data.age || 'N/A';
            document.getElementById('profileSex').textContent = data.sex || 'N/A';
            document.getElementById('profilefollow').textContent = data.followers_count || '0';
        })
        .catch(err => console.error(err));
}

// Animate posts on load
document.querySelectorAll('.post').forEach((post, index) => {
    post.style.animationDelay = `${index * 0.1}s`;
    post.style.opacity = 1;
});
