<?php
require_once '../includes/db.php';

header('Content-Type: application/json');

$q = isset($_GET['q']) ? mysqli_real_escape_string($con, $_GET['q']) : '';

if (strlen($q) > 0) {
    // Search usernames starting with OR containing the query
    $query = "SELECT user_id, username FROM tbluser WHERE username LIKE '%$q%' LIMIT 20";
    $result = mysqli_query($con, $query);
    
    $users = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $users[] = [
            'username' => $row['username'],
            'user_id' => $row['user_id']
        ];
    }
    echo json_encode($users);
} else {
    echo json_encode([]);
}
?>
