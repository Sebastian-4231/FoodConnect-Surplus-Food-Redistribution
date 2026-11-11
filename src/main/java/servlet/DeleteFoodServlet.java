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

@WebServlet("/delete-food")
public class DeleteFoodServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        // Check if user is logged in
        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String donorUsername =
                (String) session.getAttribute("username");

        String idParameter = request.getParameter("id");

        if (idParameter == null || idParameter.trim().isEmpty()) {
            response.getWriter().println("Invalid food ID.");
            return;
        }

        try {

            int foodId = Integer.parseInt(idParameter);

            String sql = "UPDATE food_items SET status = 'Removed', " +
                         "outcome = CASE WHEN outcome = 'Still Available' THEN 'Removed by Donor' ELSE outcome END " +
                         "WHERE id = ? AND donor_username = ?";

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setInt(1, foodId);
                statement.setString(2, donorUsername);

                int rowsDeleted = statement.executeUpdate();

                if (rowsDeleted > 0) {
                    response.getWriter().println(
                        "Food removed successfully."
                    );
                } else {
                    response.getWriter().println(
                        "Food not found or you are not authorized to remove it."
                    );
                }
            }

        } catch (NumberFormatException e) {

            response.getWriter().println("Invalid food ID.");

        } catch (Exception e) {

            e.printStackTrace();

            response.getWriter().println(
                "Error removing food: " + e.getMessage()
            );
        }
    }
}