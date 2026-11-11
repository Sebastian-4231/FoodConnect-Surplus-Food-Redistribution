package servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public class UserProfileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("username") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return;
        }

        String username = (String) session.getAttribute("username");
        String name = (String) session.getAttribute("name");
        String location = (String) session.getAttribute("location");
        String userType = (String) session.getAttribute("userType");

        String json = "{"
            + "\"username\":\"" + escapeJson(username) + "\","
            + "\"name\":\"" + escapeJson(name) + "\","
            + "\"location\":\"" + escapeJson(location) + "\","
            + "\"userType\":\"" + escapeJson(userType) + "\""
            + "}";

        response.getWriter().write(json);
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
