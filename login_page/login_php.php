<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - Scream</title>
    <link rel="stylesheet" href="styles.css"> <!-- Optional external CSS -->
    <style>
        body {
            font-family: Arial, sans-serif;
            background: #111;
            color: #fff;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
        }
        .container {
            background: #1a1a1a;
            padding: 30px;
            border-radius: 10px;
            width: 350px;
            box-shadow: 0 0 10px rgba(0,0,0,0.5);
        }
        h2 {
            text-align: center;
            margin-bottom: 20px;
        }
        input[type="text"], input[type="password"] {
            width: 100%;
            padding: 10px;
            margin: 10px 0;
            border: none;
            border-radius: 5px;
            background: #333;
            color: #fff;
        }
        input[type="submit"] {
            width: 100%;
            background: #ff0055;
            padding: 10px;
            border: none;
            border-radius: 5px;
            color: #fff;
            font-size: 16px;
            cursor: pointer;
        }
        input[type="submit"]:hover {
            background: #e6004c;
        }
        .error {
            color: #ff4444;
            text-align: center;
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h2>Login</h2>

        <?php if (isset($_GET['error'])): ?>
            <p class="error">Invalid username or password</p>
        <?php endif; ?>

        <form action="login_action.php" method="POST">
            <label>Username</label>
            <input type="text" name="username" required>

            <label>Password</label>
            <input type="password" name="password" required>

            <input type="submit" value="Login">
        </form>
    </div>
</body>
</html>