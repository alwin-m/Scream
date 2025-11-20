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

* ## Table: `tbl_posts`

The `tbl_posts` table stores all user-generated posts in the application. Each post is linked to a user from the `tbl_users` table.

### **SQL Definition (Corrected and Improved)**

```sql
CREATE TABLE tbl_posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    content TEXT NOT NULL,
    image VARCHAR(255) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES tbl_users(id)
);
```

### **Column Details**

| Column Name    | Type                                | Description                                               |
| -------------- | ----------------------------------- | --------------------------------------------------------- |
| **id**         | INT, Primary Key, AUTO_INCREMENT    | Unique identifier for each post.                          |
| **user_id**    | INT, Foreign Key → tbl_users.id     | Identifies the user who created the post.                 |
| **content**    | TEXT                                | The main text content of the post.                        |
| **image**      | VARCHAR(255), NULL                  | Optional image URL or file path associated with the post. |
| **created_at** | DATETIME, DEFAULT CURRENT_TIMESTAMP | Timestamp of when the post was created.                   |
| **updated_at** | DATETIME, auto-updates on edit      | Timestamp that updates when the post is modified.         |
| **is_deleted** | TINYINT(1), DEFAULT 0               | Soft delete flag. `0 = active`, `1 = deleted`.            |

### **Notes**

* Updated table reference from `users(id)` to `tbl_users(id)` to match the actual schema.
* Includes soft delete functionality for safer data handling.
* Includes automatic timestamp updates for edits.

This documentation can be committed along with the SQL file and screenshot for clear schema tracking.

<img width="1187" height="300" alt="Screenshot 2025-11-20 141209" src="https://github.com/user-attachments/assets/816fb026-010d-4a7a-a0b4-2731c57d6158" />



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
