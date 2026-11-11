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

@WebServlet("/request-food")
public class RequestFoodServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Please log in first.\"}");
            return;
        }

        String recipientUsername = (String) session.getAttribute("username");
        String userType = (String) session.getAttribute("userType");

        if (userType == null || !userType.equalsIgnoreCase("Recipient")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"success\":false,\"message\":\"Only recipients can request food.\"}");
            return;
        }

        String foodIdValue = request.getParameter("foodId");
        String requestedQtyValue = request.getParameter("requestedQuantity");
        String deliveryAddress = request.getParameter("deliveryAddress");

        if (foodIdValue == null || foodIdValue.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Food ID is required.\"}");
            return;
        }

        int foodId;
        try {
            foodId = Integer.parseInt(foodIdValue.trim());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid food ID.\"}");
            return;
        }

        if (requestedQtyValue == null || requestedQtyValue.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Requested quantity is required.\"}");
            return;
        }

        int requestedQty;
        try {
            requestedQty = Integer.parseInt(requestedQtyValue.trim());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Requested quantity must be an integer.\"}");
            return;
        }

        if (requestedQty <= 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Requested quantity must be greater than zero.\"}");
            return;
        }

        if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Delivery address is required and cannot be empty or whitespace-only.\"}");
            return;
        }
        deliveryAddress = deliveryAddress.trim();

        String selectSql = "SELECT id, quantity, price_per_unit, status FROM food_items WHERE id = ? FOR UPDATE";
        String updateQtySql = "UPDATE food_items SET quantity = ?, status = ? WHERE id = ?";
        String insertSql = "INSERT INTO food_requests " +
            "(food_id, recipient_username, requested_quantity, delivery_address, total_amount, payment_status, status, delivery_status) " +
            "VALUES (?, ?, ?, ?, ?, 'Paid', 'Pending', 'Waiting')";

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int availableQty;
                BigDecimal pricePerUnit;
                String currentStatus;

                try (PreparedStatement selectStmt = connection.prepareStatement(selectSql)) {
                    selectStmt.setInt(1, foodId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            response.getWriter().write("{\"success\":false,\"message\":\"Food item not found.\"}");
                            return;
                        }

                        currentStatus = rs.getString("status");
                        if (!"Available".equalsIgnoreCase(currentStatus)) {
                            connection.rollback();
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            response.getWriter().write("{\"success\":false,\"message\":\"This food is no longer available.\"}");
                            return;
                        }

                        String qtyStr = rs.getString("quantity");
                        try {
                            availableQty = Integer.parseInt(qtyStr.trim());
                        } catch (Exception ex) {
                            availableQty = 1;
                        }

                        pricePerUnit = rs.getBigDecimal("price_per_unit");
                        if (pricePerUnit == null) {
                            pricePerUnit = BigDecimal.ZERO;
                        }
                    }
                }

                if (requestedQty > availableQty) {
                    connection.rollback();
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"success\":false,\"message\":\"Requested quantity exceeds available quantity (" + availableQty + ").\"}");
                    return;
                }

                BigDecimal totalAmount = pricePerUnit.multiply(new BigDecimal(requestedQty)).setScale(2);
                int remainingQty = availableQty - requestedQty;
                String nextStatus = (remainingQty == 0) ? "Requested" : "Available";

                try (PreparedStatement updateStmt = connection.prepareStatement(updateQtySql)) {
                    updateStmt.setString(1, String.valueOf(remainingQty));
                    updateStmt.setString(2, nextStatus);
                    updateStmt.setInt(3, foodId);
                    updateStmt.executeUpdate();
                }

                try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, foodId);
                    insertStmt.setString(2, recipientUsername);
                    insertStmt.setInt(3, requestedQty);
                    insertStmt.setString(4, deliveryAddress);
                    insertStmt.setBigDecimal(5, totalAmount);
                    insertStmt.executeUpdate();
                }

                connection.commit();

                String jsonResp = "{"
                    + "\"success\":true,"
                    + "\"message\":\"Payment Successful — Order Confirmed\","
                    + "\"requestedQuantity\":" + requestedQty + ","
                    + "\"totalAmount\":\"" + totalAmount.toPlainString() + "\","
                    + "\"paymentStatus\":\"Paid\","
                    + "\"deliveryAddress\":\"" + escapeJson(deliveryAddress) + "\""
                    + "}";
                response.getWriter().write(jsonResp);

            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (Exception rbEx) {
                    rbEx.printStackTrace();
                }
                throw e;
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Unable to process payment and request.\"}");
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
