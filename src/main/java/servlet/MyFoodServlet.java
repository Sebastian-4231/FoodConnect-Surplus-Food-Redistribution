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
import java.sql.ResultSet;

@WebServlet("/my-food")
public class MyFoodServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String donorUsername = (String) session.getAttribute("username");

        response.setContentType("text/html;charset=UTF-8");

        try {
            String sql = "SELECT id, food_name, quantity, location, description, " +
                         "price_per_unit, status FROM food_items " +
                         "WHERE donor_username = ? AND status = 'Available' " +
                         "ORDER BY id DESC";

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, donorUsername);
                ResultSet result = statement.executeQuery();

                boolean hasFood = false;

                while (result.next()) {
                    hasFood = true;

                    int id = result.getInt("id");
                    String foodName = escapeHtml(result.getString("food_name"));
                    String quantity = escapeHtml(result.getString("quantity"));
                    String location = escapeHtml(result.getString("location"));
                    String description = result.getString("description");
                    BigDecimal price = result.getBigDecimal("price_per_unit");
                    String status = escapeHtml(result.getString("status"));

                    String priceDisplay;
                    if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
                        priceDisplay = "Free (₹0.00)";
                    } else {
                        priceDisplay = "₹ " + price.setScale(2).toPlainString() + " / unit";
                    }

                    response.getWriter().println("<div class='food-card'>");
                    response.getWriter().println("<h2>" + foodName + "</h2>");

                    if (description != null && !description.trim().isEmpty()) {
                        response.getWriter().println("<p class='food-desc'><em>" +
                            escapeHtml(description.trim()) + "</em></p>");
                    }

                    response.getWriter().println("<p><strong>Available Quantity:</strong> " +
                        quantity + " unit(s)</p>");
                    response.getWriter().println("<p><strong>Price:</strong> " +
                        priceDisplay + "</p>");
                    response.getWriter().println("<p><strong>Pickup Location:</strong> " +
                        location + "</p>");
                    response.getWriter().println("<p class='food-status'>" + status + "</p>");
                    response.getWriter().println("<button onclick=\"deleteFood(" +
                        id + ")\">Remove Food</button>");
                    response.getWriter().println("</div>");
                }

                if (!hasFood) {
                    response.getWriter().println(
                        "<div class='no-food'>" +
                        "<h2>No Food Added</h2>" +
                        "<p>You have not added any available food yet.</p>" +
                        "<button onclick=\"location.href='add-food.html'\">Add Food</button>" +
                        "</div>"
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().println(
                "<div class='no-food'>" +
                "<h2>Error Loading Food</h2>" +
                "<p>" + escapeHtml(e.getMessage()) + "</p>" +
                "</div>"
            );
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}