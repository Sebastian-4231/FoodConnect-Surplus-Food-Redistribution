package servlet;

import service.DBConnection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/add-food")
public class AddFoodServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String donorUsername = (String) session.getAttribute("username");
        String foodName = request.getParameter("foodName");
        String quantity = request.getParameter("quantity");
        String location = request.getParameter("location");
        String description = request.getParameter("description");
        String priceStr = request.getParameter("price");

        if (foodName == null || foodName.trim().isEmpty() ||
            quantity == null || quantity.trim().isEmpty() ||
            location == null || location.trim().isEmpty()) {
            response.sendRedirect("add-food.html?error=missing_fields");
            return;
        }

        BigDecimal pricePerUnit = BigDecimal.ZERO;
        if (priceStr != null && !priceStr.trim().isEmpty()) {
            try {
                pricePerUnit = new BigDecimal(priceStr.trim());
                if (pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
                    response.sendRedirect("add-food.html?error=invalid_price");
                    return;
                }
            } catch (NumberFormatException e) {
                response.sendRedirect("add-food.html?error=invalid_price");
                return;
            }
        }

        // Validate quantity is a positive integer
        try {
            int qtyInt = Integer.parseInt(quantity.trim());
            if (qtyInt <= 0) {
                response.sendRedirect("add-food.html?error=invalid_quantity");
                return;
            }
            quantity = String.valueOf(qtyInt);
        } catch (NumberFormatException e) {
            // Keep existing quantity value trimmed
            quantity = quantity.trim();
        }

        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }

        try {
            int initialQty = 1;
            try {
                initialQty = Integer.parseInt(quantity.trim());
            } catch (Exception ignored) {
            }

            String sql = "INSERT INTO food_items " +
                         "(donor_username, food_name, quantity, initial_quantity, quantity_unit, " +
                         "location, description, price_per_unit, status, created_at, outcome) " +
                         "VALUES (?, ?, ?, ?, 'units', ?, ?, ?, 'Available', NOW(), 'Still Available')";

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, donorUsername);
                statement.setString(2, foodName.trim());
                statement.setString(3, quantity);
                statement.setInt(4, initialQty);
                statement.setString(5, location.trim());
                statement.setString(6, description);
                statement.setBigDecimal(7, pricePerUnit);

                statement.executeUpdate();
            }

            response.sendRedirect("my-food.html?added=success");

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            response.getWriter().println("<h2>Food Addition Error</h2>");
            response.getWriter().println("<p>" + e.getMessage() + "</p>");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("add-food.html");
    }
}