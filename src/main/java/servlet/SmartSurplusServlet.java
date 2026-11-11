package servlet;

import service.SurplusAnalyticsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet("/smart-surplus")
public class SmartSurplusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Please log in first.\"}");
            return;
        }

        String userType = (String) session.getAttribute("userType");
        if (userType == null || !userType.equalsIgnoreCase("Donor")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"success\":false,\"message\":\"Access restricted to donors only.\"}");
            return;
        }

        String donorUsername = (String) session.getAttribute("username");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        String foodFilter = request.getParameter("foodFilter");

        try {
            Map<String, Object> data = SurplusAnalyticsService.getDonorOverview(
                donorUsername, startDate, endDate, foodFilter
            );
            response.getWriter().write(toJson(data));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Unable to load surplus analytics.\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Please log in first.\"}");
            return;
        }

        String userType = (String) session.getAttribute("userType");
        if (userType == null || !userType.equalsIgnoreCase("Donor")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"success\":false,\"message\":\"Only donors can record food outcomes.\"}");
            return;
        }

        String donorUsername = (String) session.getAttribute("username");
        String foodIdStr = request.getParameter("foodId");
        String outcome = request.getParameter("outcome");
        String wastedQtyStr = request.getParameter("wastedQuantity");
        String notes = request.getParameter("notes");

        if (foodIdStr == null || foodIdStr.trim().isEmpty() || outcome == null || outcome.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Food ID and Outcome are required.\"}");
            return;
        }

        int foodId;
        int wastedQty = 0;
        try {
            foodId = Integer.parseInt(foodIdStr.trim());
            if (wastedQtyStr != null && !wastedQtyStr.trim().isEmpty()) {
                wastedQty = Integer.parseInt(wastedQtyStr.trim());
                if (wastedQty < 0) wastedQty = 0;
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid quantity or food ID format.\"}");
            return;
        }

        try {
            boolean updated = SurplusAnalyticsService.recordOutcome(
                foodId, donorUsername, outcome.trim(), wastedQty, notes
            );

            if (updated) {
                response.getWriter().write("{\"success\":true,\"message\":\"Food outcome recorded successfully.\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"success\":false,\"message\":\"Food item not found or unauthorized.\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"Failed to record outcome.\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
