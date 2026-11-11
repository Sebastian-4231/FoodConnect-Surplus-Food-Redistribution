package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SurplusAnalyticsService {

    public static Map<String, Object> getDonorOverview(String donorUsername, String startDate,
                                                       String endDate, String foodFilter) {
        Map<String, Object> data = new HashMap<>();
        try (Connection conn = DBConnection.getConnection()) {
            data.put("metrics", calculateMetrics(conn, donorUsername, startDate, endDate, foodFilter));
            data.put("topFoods", getTopSurplusFoods(conn, donorUsername, startDate, endDate));
            data.put("trends", getWeeklyTrends(conn, donorUsername));
            data.put("history", getSurplusHistory(conn, donorUsername, startDate, endDate, foodFilter));
            data.put("recommendations", RecommendationEngine.generateRecommendations(conn, donorUsername));
        } catch (Exception e) {
            e.printStackTrace();
            data.put("error", e.getMessage());
        }
        return data;
    }

    private static Map<String, Object> calculateMetrics(Connection conn, String donorUsername,
                                                        String startDate, String endDate,
                                                        String foodFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(SUM(fi.initial_quantity), 0) AS total_listed, " +
            "  COALESCE(SUM(CASE WHEN fi.status = 'Available' AND fi.outcome = 'Still Available' " +
            "                    THEN CAST(fi.quantity AS SIGNED) ELSE 0 END), 0) AS total_remaining, " +
            "  COALESCE(SUM(fi.wasted_quantity), 0) AS total_wasted, " +
            "  COALESCE((SELECT SUM(fr.requested_quantity) FROM food_requests fr " +
            "            JOIN food_items fi2 ON fr.food_id = fi2.id " +
            "            WHERE fi2.donor_username = ? AND fr.delivery_status = 'Delivered'), 0) AS total_delivered, " +
            "  COALESCE((SELECT SUM(fr2.requested_quantity) FROM food_requests fr2 " +
            "            JOIN food_items fi3 ON fr2.food_id = fi3.id " +
            "            WHERE fi3.donor_username = ?), 0) AS total_requested " +
            "FROM food_items fi WHERE fi.donor_username = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(donorUsername); params.add(donorUsername); params.add(donorUsername);

        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at >= ? ");
            params.add(startDate.trim() + " 00:00:00");
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at <= ? ");
            params.add(endDate.trim() + " 23:59:59");
        }
        if (foodFilter != null && !foodFilter.trim().isEmpty()) {
            sql.append(" AND LOWER(fi.food_name) LIKE ? ");
            params.add("%" + foodFilter.trim().toLowerCase() + "%");
        }

        Map<String, Object> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int totalListed = rs.getInt("total_listed");
                    int totalDelivered = rs.getInt("total_delivered");
                    double redistributionRate = (totalListed > 0)
                        ? ((double) totalDelivered / totalListed) * 100.0 : 0.0;

                    map.put("totalListed", totalListed);
                    map.put("totalDelivered", totalDelivered);
                    map.put("totalRequested", rs.getInt("total_requested"));
                    map.put("totalRemaining", rs.getInt("total_remaining"));
                    map.put("totalWasted", rs.getInt("total_wasted"));
                    map.put("redistributionRate", roundOne(redistributionRate));
                    map.put("unit", "units");
                }
            }
        }
        return map;
    }

    public static List<Map<String, Object>> getTopSurplusFoods(Connection conn, String donorUsername,
                                                               String startDate, String endDate) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT fi.food_name, fi.quantity_unit, SUM(fi.initial_quantity) AS listed_qty, " +
            "  COALESCE(SUM(del.delivered_qty), 0) AS delivered_qty, " +
            "  (SUM(fi.initial_quantity) - COALESCE(SUM(del.delivered_qty), 0)) AS surplus_qty " +
            "FROM food_items fi " +
            "LEFT JOIN (SELECT food_id, SUM(requested_quantity) AS delivered_qty FROM food_requests " +
            "           WHERE delivery_status = 'Delivered' GROUP BY food_id) del ON fi.id = del.food_id " +
            "WHERE fi.donor_username = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(donorUsername);

        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at >= ? ");
            params.add(startDate.trim() + " 00:00:00");
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at <= ? ");
            params.add(endDate.trim() + " 23:59:59");
        }

        sql.append(" GROUP BY fi.food_name, fi.quantity_unit ORDER BY surplus_qty DESC LIMIT 5");

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("foodName", rs.getString("food_name"));
                    item.put("unit", rs.getString("quantity_unit"));
                    item.put("listedQty", rs.getInt("listed_qty"));
                    item.put("deliveredQty", rs.getInt("delivered_qty"));
                    item.put("surplusQty", rs.getInt("surplus_qty"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public static List<Map<String, Object>> getWeeklyTrends(Connection conn, String donorUsername) throws SQLException {
        String sql =
            "SELECT YEARWEEK(fi.created_at, 1) AS yw, DATE_FORMAT(MIN(fi.created_at), '%b %d') AS week_start, " +
            "  SUM(fi.initial_quantity) AS listed_qty, COALESCE(SUM(del.delivered_qty), 0) AS delivered_qty, " +
            "  SUM(fi.wasted_quantity) AS wasted_qty " +
            "FROM food_items fi " +
            "LEFT JOIN (SELECT food_id, SUM(requested_quantity) AS delivered_qty FROM food_requests " +
            "           WHERE delivery_status = 'Delivered' GROUP BY food_id) del ON fi.id = del.food_id " +
            "WHERE fi.donor_username = ? GROUP BY YEARWEEK(fi.created_at, 1) ORDER BY yw ASC LIMIT 8";

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, donorUsername);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    int listed = rs.getInt("listed_qty");
                    int delivered = rs.getInt("delivered_qty");
                    item.put("weekLabel", "Wk of " + rs.getString("week_start"));
                    item.put("listedQty", listed);
                    item.put("deliveredQty", delivered);
                    item.put("surplusQty", Math.max(0, listed - delivered));
                    item.put("wastedQty", rs.getInt("wasted_qty"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public static List<Map<String, Object>> getSurplusHistory(Connection conn, String donorUsername,
                                                              String startDate, String endDate,
                                                              String foodFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT fi.id, fi.food_name, fi.quantity, fi.initial_quantity, fi.quantity_unit, " +
            "  fi.location, fi.status, fi.outcome, fi.wasted_quantity, fi.created_at, fi.price_per_unit, " +
            "  COALESCE(req.req_qty, 0) AS requested_quantity, COALESCE(req.del_qty, 0) AS delivered_quantity " +
            "FROM food_items fi " +
            "LEFT JOIN (SELECT food_id, SUM(requested_quantity) AS req_qty, " +
            "             SUM(CASE WHEN delivery_status = 'Delivered' THEN requested_quantity ELSE 0 END) AS del_qty " +
            "           FROM food_requests GROUP BY food_id) req ON fi.id = req.food_id " +
            "WHERE fi.donor_username = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(donorUsername);

        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at >= ? ");
            params.add(startDate.trim() + " 00:00:00");
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND fi.created_at <= ? ");
            params.add(endDate.trim() + " 23:59:59");
        }
        if (foodFilter != null && !foodFilter.trim().isEmpty()) {
            sql.append(" AND LOWER(fi.food_name) LIKE ? ");
            params.add("%" + foodFilter.trim().toLowerCase() + "%");
        }

        sql.append(" ORDER BY fi.created_at DESC, fi.id DESC");

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("foodName", rs.getString("food_name"));
                    item.put("initialQuantity", rs.getInt("initial_quantity"));
                    item.put("remainingQuantity", rs.getString("quantity"));
                    item.put("unit", rs.getString("quantity_unit"));
                    item.put("requestedQuantity", rs.getInt("requested_quantity"));
                    item.put("deliveredQuantity", rs.getInt("delivered_quantity"));
                    item.put("outcome", rs.getString("outcome"));
                    item.put("wastedQuantity", rs.getInt("wasted_quantity"));
                    item.put("status", rs.getString("status"));
                    item.put("location", rs.getString("location"));
                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    BigDecimal price = rs.getBigDecimal("price_per_unit");
                    item.put("pricePerUnit", price != null ? price.setScale(2).toPlainString() : "0.00");
                    list.add(item);
                }
            }
        }
        return list;
    }

    public static boolean recordOutcome(int foodId, String donorUsername, String outcome,
                                        int wastedQty, String notes) throws SQLException {
        String selectSql = "SELECT quantity, status, outcome FROM food_items WHERE id = ? AND donor_username = ?";
        String updateSql = "UPDATE food_items SET outcome = ?, wasted_quantity = ?, " +
                           "status = CASE WHEN ? IN ('Discarded / Wasted', 'Used Internally') THEN 'Closed' ELSE status END, " +
                           "outcome_date = NOW(), outcome_notes = ? WHERE id = ? AND donor_username = ?";

        try (Connection conn = DBConnection.getConnection()) {
            int currentRemaining = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, foodId);
                ps.setString(2, donorUsername);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    try { currentRemaining = Integer.parseInt(rs.getString("quantity").trim()); } catch (Exception ignored) {}
                }
            }

            if ("Discarded / Wasted".equalsIgnoreCase(outcome)) {
                if (wastedQty <= 0 || wastedQty > currentRemaining) wastedQty = currentRemaining;
            } else {
                wastedQty = 0;
            }

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, outcome);
                ps.setInt(2, wastedQty);
                ps.setString(3, outcome);
                ps.setString(4, notes != null ? notes.trim() : null);
                ps.setInt(5, foodId);
                ps.setString(6, donorUsername);
                return ps.executeUpdate() > 0;
            }
        }
    }

    private static double roundOne(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
