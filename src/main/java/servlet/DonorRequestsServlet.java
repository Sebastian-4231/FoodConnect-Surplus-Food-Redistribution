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

@WebServlet("/donor-requests")
public class DonorRequestsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String donorUsername = (String) session.getAttribute("username");
        String userType = (String) session.getAttribute("userType");

        if (userType == null || !userType.equalsIgnoreCase("Donor")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String sql =
            "SELECT fr.id AS request_id, fi.food_name, fi.location AS pickup_location, " +
            "fr.requested_quantity, COALESCE(fr.delivery_address, u.location) AS delivery_address, " +
            "fr.total_amount, fr.payment_status, " +
            "u.name AS recipient_name, u.username AS recipient_username, " +
            "fr.status, fr.delivery_status, fr.request_date " +
            "FROM food_requests fr " +
            "JOIN food_items fi ON fr.food_id = fi.id " +
            "JOIN users u ON fr.recipient_username = u.username " +
            "WHERE fi.donor_username = ? " +
            "ORDER BY fr.request_date DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, donorUsername);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    BigDecimal totalAmount = result.getBigDecimal("total_amount");
                    if (totalAmount == null) totalAmount = BigDecimal.ZERO;

                    json.append("{")
                        .append("\"requestId\":").append(result.getInt("request_id")).append(",")
                        .append("\"foodName\":\"").append(escapeJson(result.getString("food_name"))).append("\",")
                        .append("\"requestedQuantity\":").append(result.getInt("requested_quantity")).append(",")
                        .append("\"pickupLocation\":\"").append(escapeJson(result.getString("pickup_location"))).append("\",")
                        .append("\"recipientName\":\"").append(escapeJson(result.getString("recipient_name"))).append("\",")
                        .append("\"recipientUsername\":\"").append(escapeJson(result.getString("recipient_username"))).append("\",")
                        .append("\"deliveryAddress\":\"").append(escapeJson(result.getString("delivery_address"))).append("\",")
                        .append("\"totalAmount\":\"").append(totalAmount.setScale(2).toPlainString()).append("\",")
                        .append("\"paymentStatus\":\"").append(escapeJson(result.getString("payment_status"))).append("\",")
                        .append("\"status\":\"").append(escapeJson(result.getString("status"))).append("\",")
                        .append("\"deliveryStatus\":\"").append(escapeJson(result.getString("delivery_status"))).append("\",")
                        .append("\"requestDate\":\"").append(escapeJson(result.getString("request_date"))).append("\"")
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
                "Unable to retrieve donor requests."
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
