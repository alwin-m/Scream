# Scream

An experimental, text-first micro‑blogging platform built with **PHP** and **MySQL** (started in 2025). Scream is intentionally minimal: it focuses on written expression, fast performance, and small, auditable data models — no image uploads, no vanity metrics, and optional anonymity.

---

## What it is

Scream is a lightweight social experiment that brings conversation back to words. Users post short text messages and interact via comments and basic moderation tools. The goal is a clean, quiet place for honest expression.

---

## Key principles

* **Text-first**: no images, no videos — only text content.
* **Minimal UI**: fast, simple pages focused on reading and writing.
* **Privacy-minded**: optional anonymous posting and strict moderation controls.
* **Easy to self-host**: plain PHP + MySQL, works on most shared hosts or a small VPS.

---

## Features (MVP)

* Anonymous text posts
* Chronological feed
* Comments
* Likes (tracked with timestamps)
* View tracking (who/when a post was viewed)
* Basic reporting system for moderation
* Simple authentication and session tracking

---

## Database snapshot

The screenshot below shows the **`tbl_users`** table (user/account schema) currently in the database. This is the first table created; additional tables for posts, comments, likes, views, reports, and sessions will be added and documented with screenshots in subsequent commits.

![tbl\_users table screenshot](https://github.com/user-attachments/assets/2450ea45-159c-428e-81da-4ad25a6e9e4d)

**Screenshot notes:**

* The image displays the `tbl_users` table structure (columns such as `user_id`, `username`, `email`, `password_hash`, `dob`, `created_at`, `last_login`, `status`, and `profile_visibility`).
* This is the canonical user table for the initial MVP — future screenshots will show the `tbl_posts`, `tbl_comments`, `tbl_likes`, `tbl_views`, `tbl_reports`, and `tbl_sessions` tables as they are created.

---

## Setup (quick)

1. Clone the repo: `git clone <repo-url>`
2. Import the SQL schema (provided SQL file) into phpMyAdmin or via `mysql` CLI.
3. Update database settings in `config.php` (DB host, user, pass, name).
4. Start PHP/Apache (XAMPP/LAMP) and open the project in your browser.

---

## Contributing

Contributions are welcome. Please open issues for feature requests or bugs, and submit pull requests for fixes and improvements. Suggested early tasks:

* Add server-side validation and secure password hashing (PHP `password_hash`).
* Implement `check_username.php` to validate uniqueness when signing up.
* Add unit tests for the DB layer and simple integration tests for endpoints.

---

## Vision

Scream aims to be a calm, durable space where words carry weight again. Over time the project will expand its moderation tooling and analytics while keeping the core experience minimal and fast.

---

If you want, I can also generate the full `README.md` file in the repo, or open a follow-up PR that adds the other table screenshots and the SQL create statements for each table.
