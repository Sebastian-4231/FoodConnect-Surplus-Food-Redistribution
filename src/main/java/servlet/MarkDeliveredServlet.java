
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


public class MarkDeliveredServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain;charset=UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null ||
            session.getAttribute("username") == null) {
            response.sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Please log in first."
            );
            return;
        }

        String username =
            (String) session.getAttribute("username");

        String userType =
            (String) session.getAttribute("userType");

        if (userType == null ||
            !userType.equalsIgnoreCase("Delivery Partner")) {
            response.sendError(
                HttpServletResponse.SC_FORBIDDEN,
                "Only delivery partners can update deliveries."
            );
            return;
        }

        int requestId;

        try {
            requestId = Integer.parseInt(
                request.getParameter("requestId")
            );

            if (requestId <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            response.sendError(
                HttpServletResponse.SC_BAD_REQUEST,
                "Invalid request ID."
            );
            return;
        }

        String sql =
            "UPDATE food_requests " +
            "SET delivery_status = 'Delivered' " +
            "WHERE id = ? " +
            "AND delivery_partner_username = ? " +
            "AND delivery_status = 'Assigned'";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setInt(1, requestId);
            statement.setString(2, username);

            int updated = statement.executeUpdate();

            if (updated == 1) {
                response.setStatus(
                    HttpServletResponse.SC_OK
                );
                response.getWriter().write(
                    "Delivery marked as delivered."
                );
            } else {
                response.sendError(
                    HttpServletResponse.SC_CONFLICT,
                    "Delivery not found, not assigned to you, " +
                    "or already delivered."
                );
            }

        } catch (Exception e) {
            getServletContext().log(
                "Failed to mark delivery as delivered.", e
            );

            response.sendError(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Unable to update delivery status."
            );
        }
    }
}
