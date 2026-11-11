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

public class RecommendationEngine {

    public static List<Map<String, Object>> generateRecommendations(Connection conn, String donorUsername)
            throws SQLException {

        // Query historical listings grouped by food_name and week
        String sql =
            "SELECT " +
            "  fi.food_name, " +
            "  fi.quantity_unit, " +
            "  YEARWEEK(fi.created_at, 1) AS yw, " +
            "  SUM(fi.initial_quantity) AS week_listed, " +
            "  COALESCE(SUM(del.delivered_qty), 0) AS week_delivered, " +
            "  SUM(fi.wasted_quantity) AS week_wasted " +
            "FROM food_items fi " +
            "LEFT JOIN ( " +
            "  SELECT food_id, SUM(requested_quantity) AS delivered_qty " +
            "  FROM food_requests " +
            "  WHERE delivery_status = 'Delivered' " +
            "  GROUP BY food_id " +
            ") del ON fi.id = del.food_id " +
            "WHERE fi.donor_username = ? " +
            "  AND fi.created_at >= DATE_SUB(NOW(), INTERVAL 8 WEEK) " +
            "GROUP BY fi.food_name, fi.quantity_unit, YEARWEEK(fi.created_at, 1) " +
            "ORDER BY fi.food_name, yw DESC";

        Map<String, FoodStats> foodStatsMap = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, donorUsername);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String foodName = rs.getString("food_name");
                    String unit = rs.getString("quantity_unit");
                    int listed = rs.getInt("week_listed");
                    int delivered = rs.getInt("week_delivered");
                    int wasted = rs.getInt("week_wasted");

                    String key = foodName.toLowerCase() + "_" + unit.toLowerCase();
                    FoodStats stats = foodStatsMap.computeIfAbsent(key, k -> new FoodStats(foodName, unit));
                    stats.addWeekData(listed, delivered, wasted);
                }
            }
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (FoodStats stats : foodStatsMap.values()) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("foodName", stats.foodName);
            rec.put("unit", stats.unit);
            rec.put("weeksObserved", stats.distinctWeeks);

            if (stats.distinctWeeks < 2) {
                // Rule 1: Insufficient history
                rec.put("status", "INSUFFICIENT_DATA");
                rec.put("badge", "Baseline Building");
                rec.put("badgeClass", "badge-neutral");
                rec.put("title", "Collecting Data for " + stats.foodName);
                rec.put("recommendation",
                    "We need at least 2 distinct weeks of records to identify reliable surplus trends. " +
                    "Currently, " + stats.distinctWeeks + " week is recorded with " + stats.totalListed +
                    " " + stats.unit + " listed and " + stats.totalDelivered + " " + stats.unit + " delivered. " +
                    "Keep recording your listings to unlock personalized cooking recommendations.");
                rec.put("actionableTip", "No cooking adjustment advised yet. Continue tracking daily listings.");
                rec.put("calculationDetails",
                    "Observed Period: " + stats.distinctWeeks + " week | Total Listed: " + stats.totalListed +
                    " " + stats.unit + " | Total Delivered: " + stats.totalDelivered + " " + stats.unit);
            } else {
                double avgListed = stats.getAverageWeeklyListed();
                double avgDelivered = stats.getAverageWeeklyDelivered();
                double avgSurplus = stats.getAverageWeeklySurplus();

                if (avgSurplus > 0.5) {
                    // Rule 2: Recurring surplus detected
                    int suggestedTrim = (int) Math.max(1, Math.round(avgSurplus * 0.6));

                    rec.put("status", "ADJUST_COOKING");
                    rec.put("badge", "Actionable Insight");
                    rec.put("badgeClass", "badge-warning");
                    rec.put("title", "Opportunity to Reduce Preparation for " + stats.foodName);
                    rec.put("recommendation",
                        "Your recorded " + stats.foodName + " surplus averaged " + round(avgSurplus) +
                        " " + stats.unit + " per week across " + stats.distinctWeeks + " weeks. " +
                        "Consider reviewing your batch preparation and gradually reducing it by approximately " +
                        suggestedTrim + " " + stats.unit + " while continuing to monitor recipient requests.");
                    
                    String wasteNotice = "";
                    if (stats.totalWasted > 0) {
                        wasteNotice = " You have confirmed " + stats.totalWasted + " " + stats.unit +
                                      " discarded. Trimming preparation directly cuts this preventable waste.";
                    }

                    rec.put("actionableTip",
                        "Gradually scale batch size down by ~" + suggestedTrim + " " + stats.unit +
                        " per week and monitor daily request trends." + wasteNotice);
                    rec.put("calculationDetails",
                        "Formula: (Total Surplus " + round(stats.totalSurplus) + " " + stats.unit +
                        " / " + stats.distinctWeeks + " Weeks) = " + round(avgSurplus) + " " + stats.unit + "/week. " +
                        "Avg Listed: " + round(avgListed) + " " + stats.unit + "/wk, Avg Delivered: " +
                        round(avgDelivered) + " " + stats.unit + "/wk.");
                } else {
                    // Rule 3: Balanced sizing
                    rec.put("status", "OPTIMAL");
                    rec.put("badge", "Well Balanced");
                    rec.put("badgeClass", "badge-success");
                    rec.put("title", "Optimal Batch Sizing for " + stats.foodName);
                    rec.put("recommendation",
                        "Your recorded surplus for " + stats.foodName + " averaged only " + round(avgSurplus) +
                        " " + stats.unit + "/week across " + stats.distinctWeeks + " weeks. " +
                        "Your current cooking and listing quantities align very closely with community demand.");
                    rec.put("actionableTip", "Maintain current preparation volumes.");
                    rec.put("calculationDetails",
                        "Avg Weekly Surplus: " + round(avgSurplus) + " " + stats.unit +
                        " across " + stats.distinctWeeks + " weeks.");
                }
            }

            recommendations.add(rec);
        }

        return recommendations;
    }

    private static double round(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static class FoodStats {
        final String foodName;
        final String unit;
        int distinctWeeks = 0;
        int totalListed = 0;
        int totalDelivered = 0;
        int totalWasted = 0;
        int totalSurplus = 0;

        FoodStats(String foodName, String unit) {
            this.foodName = foodName;
            this.unit = unit != null ? unit : "units";
        }

        void addWeekData(int listed, int delivered, int wasted) {
            distinctWeeks++;
            totalListed += listed;
            totalDelivered += delivered;
            totalWasted += wasted;
            totalSurplus += Math.max(0, listed - delivered);
        }

        double getAverageWeeklyListed() {
            return distinctWeeks > 0 ? (double) totalListed / distinctWeeks : 0.0;
        }

        double getAverageWeeklyDelivered() {
            return distinctWeeks > 0 ? (double) totalDelivered / distinctWeeks : 0.0;
        }

        double getAverageWeeklySurplus() {
            return distinctWeeks > 0 ? (double) totalSurplus / distinctWeeks : 0.0;
        }
    }
}
