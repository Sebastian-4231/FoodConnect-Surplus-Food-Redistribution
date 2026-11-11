package servlet;

import service.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {
                System.out.println("LoginServlet Post Received");
             
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try {
            // Basic server-side validation
            if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {

                response.sendRedirect("login.html?error=empty");
                return;
            }

            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, username);
                statement.setString(2, password);

                ResultSet result = statement.executeQuery();

                if (result.next()) {

                    // Create session after successful login
                    HttpSession session = request.getSession();

                    session.setAttribute("username", result.getString("username"));
                    session.setAttribute("name", result.getString("name"));
                    session.setAttribute("location", result.getString("location"));
                    session.setAttribute("userType", result.getString("user_type"));

                    String userType = result.getString("user_type");

if (userType.equalsIgnoreCase("Donor")) {
    response.sendRedirect("donor-dashboard.html");

} else if (userType.equalsIgnoreCase("Recipient")) {
    response.sendRedirect("recipient_dashboard.html");

} else if (userType.equalsIgnoreCase("Delivery Partner")) {
    response.sendRedirect("delivery_dashboard.html");

} else {
    response.sendRedirect("dashboard.jsp");
}

                } else {

                    response.sendRedirect("login.html?error=invalid");
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            response.setContentType("text/html");

            response.getWriter().println(
                "<h2>Login Error</h2>"
            );

            response.getWriter().println(
                "<p>" + e.getMessage() + "</p>"
            );
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {
            System.out.println("LoginServlet Get Received");
        response.sendRedirect("login.html");
    }
}