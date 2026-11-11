
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


@WebServlet("/accept-delivery")
public class AcceptDeliveryServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain;charset=UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null ||
            session.getAttribute("username") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                               "Please log in first.");
            return;
        }

        String username = (String) session.getAttribute("username");
        String userType = (String) session.getAttribute("userType");

        if (userType == null ||
            !userType.equalsIgnoreCase("Delivery Partner")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                               "Only delivery partners can accept deliveries.");
            return;
        }

        String idValue = request.getParameter("requestId");

        if (idValue == null || idValue.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               "Request ID is required.");
            return;
        }

        int requestId;

        try {
            requestId = Integer.parseInt(idValue);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               "Invalid request ID.");
            return;
        }

        String sql =
            "UPDATE food_requests " +
            "SET delivery_partner_username = ?, " +
            "delivery_status = 'Assigned' " +
            "WHERE id = ? " +
            "AND status = 'Pending' " +
            "AND delivery_status = 'Waiting' " +
            "AND delivery_partner_username IS NULL";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setInt(2, requestId);

            int updated = statement.executeUpdate();

            if (updated == 1) {
                response.getWriter().write(
                    "Delivery accepted successfully!"
                );
            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write(
                    "This delivery has already been accepted or is unavailable."
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Unable to accept delivery."
            );
        }
    }
}
