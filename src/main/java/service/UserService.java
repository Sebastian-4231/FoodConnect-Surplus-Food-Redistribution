package service;

import exception.InvalidUserException;
import model.User;

public class UserService implements UserOperations {

    @Override
    public boolean validateUser(User user) throws InvalidUserException {

        if (user == null) {
            throw new InvalidUserException("User information cannot be null.");
        }

        StringBuilder errors = new StringBuilder();

        if (user.getUsername() == null ||
            user.getUsername().trim().isEmpty()) {

            errors.append("Username cannot be empty.\n");
        }

        if (user.getPassword() == null ||
            user.getPassword().trim().isEmpty()) {

            errors.append("Password cannot be empty.\n");
        }

        if (user.getName() == null ||
            user.getName().trim().isEmpty()) {

            errors.append("Name cannot be empty.\n");
        }

        if (user.getLocation() == null ||
            user.getLocation().trim().isEmpty()) {

            errors.append("Location cannot be empty.\n");
        }

        if (user.getUserType() == null ||
            user.getUserType().trim().isEmpty()) {

            errors.append("User type cannot be empty.\n");
        }

        if (errors.length() > 0) {
            throw new InvalidUserException(errors.toString());
        }

        return true;
    }
}