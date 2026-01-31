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
            charCount.style.color = 'var(--text-muted)';
        }
    });
}

// Header Scroll Effect
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
        // Trigger reflow
        void modal.offsetWidth;
        modal.classList.add('active');
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('active');
        setTimeout(() => {
            modal.style.display = 'none';
        }, 300);
    }
}

// Close on outside click
window.addEventListener('click', (event) => {
    if (event.target.classList.contains('modal')) {
        closeModal(event.target.id);
    }
});

window.closeModal = closeModal;

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
        }
    });
}

function fetchSearchResults(query) {
    fetch(`actions/search_users.php?q=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(data => {
            searchResults.innerHTML = '';
            if (data.length > 0) {
                data.forEach(user => {
                    const div = document.createElement('div');
                    div.innerHTML = `<strong>${user.username}</strong>`;
                    div.addEventListener('click', () => {
                        openProfile(user.username);
                    });
                    searchResults.appendChild(div);
                });
            } else {
                searchResults.innerHTML = '<div style="padding:1rem; text-align:center; color:#888;">No users found.</div>';
            }
        })
        .catch(err => console.error('Search error:', err));
}

// Like System
document.querySelectorAll('.like-btn').forEach(btn => {
    btn.addEventListener('click', function (e) {
        e.preventDefault();
        // Check if we clicked the count span, if so, handled by propagation? 
        // No, stopPropagation is on the span.
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
                btnElement.style.transform = 'scale(1.2)';
                setTimeout(() => btnElement.style.transform = 'scale(1)', 200);

                // Toggle class for visual feedback
                if (btnElement.classList.contains('liked')) {
                    btnElement.classList.remove('liked');
                } else {
                    btnElement.classList.add('liked');
                }
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
                li.style.padding = '0.5rem 0';
                li.style.borderBottom = '1px solid var(--glass-border)';
                list.appendChild(li);
            });
        });
}

document.querySelectorAll('.username').forEach(userElem => {
    userElem.addEventListener('click', () => {
        const username = userElem.getAttribute('data-username');
        openProfile(username);
    });
});

function openProfile(username) {
    openModal('profileModal');
    document.getElementById('profileUsername').textContent = username;

    // Reset placeholders
    document.getElementById('profileAge').textContent = '...';
    document.getElementById('profileSex').textContent = '...';
    document.getElementById('profilefollow').textContent = '...';

    // Fetch Details
    fetch(`actions/get_user.php?username=${encodeURIComponent(username)}`)
        .then(res => res.json())
        .then(data => {
            document.getElementById('profileAge').textContent = data.age || 'N/A';
            document.getElementById('profileSex').textContent = data.sex || 'N/A';
            document.getElementById('profilefollow').textContent = data.followers_count || data.follow || '0';
        })
        .catch(err => console.error(err));
}
