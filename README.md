# Scream  🐙 

An experimental, text-first micro‑blogging platform built with **PHP** and **MySQL** (started in 2025). Scream is intentionally minimal: it focuses on written expression, fast performance, and small, auditable data models —  image uploads and optional anonymity.

---

## What it is

Scream is a lightweight social experiment that brings conversation back to words. Users post short text messages and interact via comments and basic moderation tools. The goal is a clean, quiet place for honest expression.

---

## Key principles

* **Text-first**:  images, no videos — only text and images content.
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

### **Table: `tbl_users`**

The core user/account storage for Scream.

#### **SQL Definition**

```sql
CREATE TABLE tbl_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    dob DATE DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME DEFAULT NULL,
    status ENUM('active','banned','inactive') DEFAULT 'active',
    profile_visibility ENUM('public','private') DEFAULT 'public'
);
```

#### **Column Details**

| Column Name            | Type                    | Description                        |
| ---------------------- | ----------------------- | ---------------------------------- |
| **id**                 | INT, PK, AUTO_INCREMENT | Unique user ID                     |
| **username**           | VARCHAR(50), UNIQUE     | Visible name for login + display   |
| **email**              | VARCHAR(100), UNIQUE    | User email address                 |
| **password_hash**      | VARCHAR(255)            | Secure hashed password             |
| **dob**                | DATE                    | Optional date of birth             |
| **created_at**         | DATETIME                | Auto timestamp on creation         |
| **last_login**         | DATETIME                | Timestamp of last login            |
| **status**             | ENUM                    | User state: active/banned/inactive |
| **profile_visibility** | ENUM                    | public/private profile             |

![tbl\_users table screenshot](https://github.com/user-attachments/assets/2450ea45-159c-428e-81da-4ad25a6e9e4d)

---

## Table: `tbl_posts`

Stores all written posts.

### **SQL Definition**

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

| Column Name    | Type                    | Description               |
| -------------- | ----------------------- | ------------------------- |
| **id**         | INT, PK, AUTO_INCREMENT | Unique post ID            |
| **user_id**    | INT, FK                 | References `tbl_users.id` |
| **content**    | TEXT                    | The main post text        |
| **image**      | VARCHAR(255), NULL      | Optional image path       |
| **created_at** | DATETIME                | Auto timestamp            |
| **updated_at** | DATETIME                | Auto-updated on edit      |
| **is_deleted** | TINYINT(1)              | Soft delete flag          |

<img width="1187" height="300" alt="Screenshot 2025-11-20 141209" src="https://github.com/user-attachments/assets/eb5ff0f7-eae2-4213-824b-9228a5bce80d" />

---

## Table: `tbl_comments`

Stores all comments on posts, including threaded replies.

### **SQL Definition**

```sql
CREATE TABLE tbl_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    content TEXT NOT NULL,
    is_edited TINYINT(1) DEFAULT 0,
    is_deleted TINYINT(1) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,
    FOREIGN KEY (post_id) REFERENCES tbl_posts(id),
    FOREIGN KEY (user_id) REFERENCES tbl_users(id),
    FOREIGN KEY (parent_id) REFERENCES tbl_comments(id)
);
```

### **Column Details**

| Column Name    | Type                       | Description                                  |
| -------------- | -------------------------- | -------------------------------------------- |
| **id**         | BIGINT, PK, AUTO_INCREMENT | Unique comment ID                            |
| **post_id**    | BIGINT, FK                 | References tbl_posts.id                      |
| **user_id**    | BIGINT, FK                 | References tbl_users.id                      |
| **parent_id**  | BIGINT, FK/NULL            | If NULL = top-level comment; otherwise reply |
| **content**    | TEXT                       | Comment text                                 |
| **is_edited**  | TINYINT(1)                 | 1 = edited, 0 = original                     |
| **is_deleted** | TINYINT(1)                 | Soft delete flag                             |
| **created_at** | DATETIME                   | Timestamp of creation                        |
| **updated_at** | DATETIME                   | Timestamp when edited                        |

<img width="1226" height="362" alt="Screenshot 2025-11-21 002934" src="https://github.com/user-attachments/assets/d3c6e425-1d7c-48a3-b10c-8a24a5b11498" />


---

## Setup (quick)

1. Clone the repo: `git clone <repo-url>`
2. Import the SQL schema into phpMyAdmin or MySQL CLI.
3. Update DB settings in `config.php`.
4. Start PHP/Apache and open in a browser.

---

## Contributing

Contributions are welcome. Submit issues or PRs. Suggested early tasks:

* Add server-side validation
* Improve session handling
* Add tests for DB and endpoints

---

## Vision

A calm, durable place where words matter. Future updates add moderation tools and analytics without sacrificing simplicity.

---

If you'd like, I can extend this README with ERD diagrams, architecture diagrams, API route documentation, or future table definitions.
