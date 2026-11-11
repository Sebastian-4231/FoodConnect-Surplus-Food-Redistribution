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

@WebServlet("/available-deliveries")
public class AvailableDeliveriesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String userType = (String) session.getAttribute("userType");

        if (userType == null || !userType.equalsIgnoreCase("Delivery Partner")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String sql =
            "SELECT fr.id AS request_id, fi.food_name, fr.requested_quantity, " +
            "fi.location AS pickup_location, " +
            "COALESCE(fr.delivery_address, u.location) AS delivery_address, " +
            "fr.delivery_status, fr.request_date " +
            "FROM food_requests fr " +
            "JOIN food_items fi ON fr.food_id = fi.id " +
            "JOIN users u ON fr.recipient_username = u.username " +
            "WHERE fr.status = 'Pending' " +
            "AND fr.delivery_status = 'Waiting' " +
            "AND fr.delivery_partner_username IS NULL " +
            "ORDER BY fr.request_date DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                if (!first) {
                    json.append(",");
                }

                json.append("{")
                    .append("\"requestId\":").append(result.getInt("request_id")).append(",")
                    .append("\"foodName\":\"").append(escapeJson(result.getString("food_name"))).append("\",")
                    .append("\"requestedQuantity\":").append(result.getInt("requested_quantity")).append(",")
                    .append("\"pickupLocation\":\"").append(escapeJson(result.getString("pickup_location"))).append("\",")
                    .append("\"deliveryAddress\":\"").append(escapeJson(result.getString("delivery_address"))).append("\",")
                    .append("\"deliveryStatus\":\"").append(escapeJson(result.getString("delivery_status"))).append("\",")
                    .append("\"requestDate\":\"").append(escapeJson(result.getString("request_date"))).append("\"")
                    .append("}");

                first = false;
            }

            json.append("]");
            response.getWriter().write(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Unable to retrieve available deliveries."
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
