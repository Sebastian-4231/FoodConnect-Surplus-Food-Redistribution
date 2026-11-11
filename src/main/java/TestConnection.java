import java.sql.Connection;
import service.DBConnection;

public class TestConnection {

    public static void main(String[] args) {

        try {
            Connection connection = DBConnection.getConnection();

            System.out.println("MySQL connection successful!");

            connection.close();

        } catch (Exception e) {
            System.out.println("MySQL connection failed.");
            e.printStackTrace();
        }
    }
}