package servlet;

import service.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {

        String name = request.getParameter("name");
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String location = request.getParameter("location");
        String userType = request.getParameter("userType");

        try {

            // Server-side validation
            if (name == null || name.trim().isEmpty() ||
                username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty() ||
                location == null || location.trim().isEmpty() ||
                userType == null || userType.trim().isEmpty()) {

                response.sendRedirect("register.html?error=empty");
                return;
            }

            String sql = "INSERT INTO users " +
                         "(name, username, password, location, user_type) " +
                         "VALUES (?, ?, ?, ?, ?)";

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, name);
                statement.setString(2, username);
                statement.setString(3, password);
                statement.setString(4, location);
                statement.setString(5, userType);

                statement.executeUpdate();

                response.sendRedirect("login.html?registered=success");
            }

        } catch (Exception e) {

            e.printStackTrace();

            response.setContentType("text/html");

            response.getWriter().println("<h2>Registration Error</h2>");
            response.getWriter().println(
                    "<p>" + e.getMessage() + "</p>"
            );
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        response.sendRedirect("register.html");
    }
}