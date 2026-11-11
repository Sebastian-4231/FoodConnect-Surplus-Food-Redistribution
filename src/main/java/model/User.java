package model;
public class User {

    String username;
    String password;
    String name;
    String location;
    String userType;

    // Constructor
    public User(String username, String password, String name,
                String location, String userType) {

        this.username = username;
        this.password = password;
        this.name = name;
        this.location = location;
        this.userType = userType;
    }

    // Validation method
    public boolean validateUser() {

    boolean valid = true;

    if (username == null || username.trim().isEmpty()) {
        System.out.println("Username cannot be empty.");
        valid = false;
    }

    if (password == null || password.trim().isEmpty()) {
        System.out.println("Password cannot be empty.");
        valid = false;
    }

    if (name == null || name.trim().isEmpty()) {
        System.out.println("Name cannot be empty.");
        valid = false;
    }

    if (location == null || location.trim().isEmpty()) {
        System.out.println("Location cannot be empty.");
        valid = false;
    }

    if (userType == null || userType.trim().isEmpty()) {
        System.out.println("User type cannot be empty.");
        valid = false;
    }

    return valid;
}

    // Display method
    public void displayUser() {
        System.out.println("Username: " + username);
        System.out.println("Name: " + name);
        System.out.println("Location: " + location);
        System.out.println("User Type: " + userType);
    }
    public String getUsername() {
    return username;
}

public String getPassword() {
    return password;
}

public String getName() {
    return name;
}

public String getLocation() {
    return location;
}

public String getUserType() {
    return userType;
}
}