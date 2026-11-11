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

@WebServlet("/my-requests")
public class MyRequestsServlet extends HttpServlet {

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

        String username = (String) session.getAttribute("username");

        String sql =
            "SELECT fr.id, fi.food_name, fr.requested_quantity, fi.location AS pickup_location, " +
            "COALESCE(fr.delivery_address, u.location) AS delivery_address, " +
            "fr.total_amount, fr.payment_status, " +
            "fr.status, fr.delivery_status, fr.request_date " +
            "FROM food_requests fr " +
            "JOIN food_items fi ON fr.food_id = fi.id " +
            "JOIN users u ON fr.recipient_username = u.username " +
            "WHERE fr.recipient_username = ? " +
            "ORDER BY fr.request_date DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                    if (totalAmount == null) totalAmount = BigDecimal.ZERO;

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"foodName\":\"").append(escapeJson(rs.getString("food_name"))).append("\",")
                        .append("\"requestedQuantity\":").append(rs.getInt("requested_quantity")).append(",")
                        .append("\"pickupLocation\":\"").append(escapeJson(rs.getString("pickup_location"))).append("\",")
                        .append("\"deliveryAddress\":\"").append(escapeJson(rs.getString("delivery_address"))).append("\",")
                        .append("\"totalAmount\":\"").append(totalAmount.setScale(2).toPlainString()).append("\",")
                        .append("\"paymentStatus\":\"").append(escapeJson(rs.getString("payment_status"))).append("\",")
                        .append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",")
                        .append("\"deliveryStatus\":\"").append(escapeJson(rs.getString("delivery_status"))).append("\",")
                        .append("\"requestDate\":\"").append(escapeJson(rs.getString("request_date"))).append("\"")
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
                "Unable to retrieve food requests."
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
