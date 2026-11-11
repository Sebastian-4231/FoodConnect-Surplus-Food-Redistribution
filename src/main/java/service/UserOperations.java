package service;

import exception.InvalidUserException;
import model.User;

public interface UserOperations {

    boolean validateUser(User user) throws InvalidUserException;

}