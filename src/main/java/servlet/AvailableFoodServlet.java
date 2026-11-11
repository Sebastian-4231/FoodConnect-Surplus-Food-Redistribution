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

@WebServlet("/available-food")
public class AvailableFoodServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String location = request.getParameter("location");

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        if (location == null || location.trim().isEmpty()) {
            response.getWriter().write("[]");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        String sql = "SELECT id, food_name, quantity, location, description, price_per_unit " +
                     "FROM food_items " +
                     "WHERE location = ? AND status = 'Available' " +
                     "ORDER BY id DESC";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, location.trim());

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    BigDecimal price = result.getBigDecimal("price_per_unit");
                    if (price == null) {
                        price = BigDecimal.ZERO;
                    }

                    json.append("{")
                        .append("\"id\":").append(result.getInt("id")).append(",")
                        .append("\"foodName\":\"").append(escapeJson(result.getString("food_name"))).append("\",")
                        .append("\"quantity\":\"").append(escapeJson(result.getString("quantity"))).append("\",")
                        .append("\"location\":\"").append(escapeJson(result.getString("location"))).append("\",")
                        .append("\"description\":\"").append(escapeJson(result.getString("description"))).append("\",")
                        .append("\"pricePerUnit\":").append(price.setScale(2).toPlainString())
                        .append("}");

                    first = false;
                }
            }

            json.append("]");
            response.getWriter().write(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Unable to retrieve available food."
            );
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
