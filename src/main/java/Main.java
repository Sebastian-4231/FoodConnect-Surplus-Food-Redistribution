import java.util.Scanner;

import exception.InvalidUserException;
import model.User;
import service.UserService;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter username: ");
        String username = sc.nextLine();

        System.out.print("Enter password: ");
        String password = sc.nextLine();

        System.out.print("Enter name: ");
        String name = sc.nextLine();

        System.out.print("Enter location: ");
        String location = sc.nextLine();

        System.out.print("Enter user type (Donor/Recipient): ");
        String userType = sc.nextLine();

        User user1 = new User(
                username,
                password,
                name,
                location,
                userType
        );

        UserService userService = new UserService();

        try {

            if (userService.validateUser(user1)) {

                System.out.println("\nUser is valid.");
                user1.displayUser();

            }

        } catch (InvalidUserException e) {

            System.out.println("\nInvalid User: " + e.getMessage());

        }

        sc.close();
    }
}