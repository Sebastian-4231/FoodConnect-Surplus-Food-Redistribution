<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>FoodConnect Dashboard</title>
</head>

<body>

    <h1>Welcome to FoodConnect</h1>

    <h2>Welcome, ${sessionScope.name}!</h2>

    <p>Username: ${sessionScope.username}</p>
    <p>Location: ${sessionScope.location}</p>
    <p>User Type: ${sessionScope.userType}</p>

    <br>

    <a href="logout">Logout</a>

</body>
</html>