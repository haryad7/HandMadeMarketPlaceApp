package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;

/**
 * Admin reports: stat tiles + bar chart (revenue per shop)
 * + pie chart (orders by status) + recent orders table.
 */
public class ReportsController {

    public static class RecentRow {
        private final int    orderId;
        private final String buyer, shop, status, date;
        private final double total;
        public RecentRow(int orderId, String buyer, String shop,
                         double total, String status, String date) {
            this.orderId = orderId; this.buyer = buyer; this.shop = shop;
            this.total = total; this.status = status; this.date = date;
        }
        public int    getOrderId() { return orderId; }
        public String getBuyer()   { return buyer; }
        public String getShop()    { return shop; }
        public double getTotal()   { return total; }
        public String getStatus()  { return status; }
        public String getDate()    { return date; }
    }

    @FXML private Label     revenueLabel, ordersLabel, paidLabel, usersLabel;
    @FXML private BarChart<String, Number> revenueChart;
    @FXML private CategoryAxis revenueX;
    @FXML private NumberAxis   revenueY;
    @FXML private PieChart    statusChart;
    @FXML private TableView<RecentRow> recentTable;
    @FXML private TableColumn<RecentRow, Integer> rColId;
    @FXML private TableColumn<RecentRow, String>  rColBuyer, rColShop, rColStatus, rColDate;
    @FXML private TableColumn<RecentRow, Double>  rColTotal;

    @FXML
    public void initialize() {
        rColId    .setCellValueFactory(new PropertyValueFactory<>("orderId"));
        rColBuyer .setCellValueFactory(new PropertyValueFactory<>("buyer"));
        rColShop  .setCellValueFactory(new PropertyValueFactory<>("shop"));
        rColTotal .setCellValueFactory(new PropertyValueFactory<>("total"));
        rColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        rColDate  .setCellValueFactory(new PropertyValueFactory<>("date"));
        rColTotal.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? "" : String.format("$%.2f", v));
            }
        });
        loadAll();
    }

    @FXML
    public void loadAll() {
        try (Connection c = DBConnection.getConnection()) {

            // ── Tiles
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT " +
                         " (SELECT COALESCE(SUM(total_amount),0) FROM orders WHERE status IN ('paid','shipped','completed')) AS revenue," +
                         " (SELECT COUNT(*) FROM orders)                                          AS orders_total," +
                         " (SELECT COUNT(*) FROM orders WHERE status IN ('paid','shipped','completed')) AS orders_paid," +
                         " (SELECT COUNT(*) FROM users)                                           AS users_total")) {
                if (rs.next()) {
                    revenueLabel.setText(String.format("$%,.2f", rs.getDouble("revenue")));
                    ordersLabel .setText(String.valueOf(rs.getInt("orders_total")));
                    paidLabel   .setText(String.valueOf(rs.getInt("orders_paid")));
                    usersLabel  .setText(String.valueOf(rs.getInt("users_total")));
                }
            }

            // ── Bar chart: revenue per shop
            revenueChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT sh.name AS shop, SUM(o.total_amount) AS rev " +
                         "FROM orders o JOIN shops sh ON o.shop_id = sh.shop_id " +
                         "WHERE o.status IN ('paid','shipped','completed') " +
                         "GROUP BY sh.shop_id, sh.name " +
                         "ORDER BY rev DESC LIMIT 6")) {
                while (rs.next()) {
                    series.getData().add(new XYChart.Data<>(
                            rs.getString("shop"), rs.getDouble("rev")));
                }
            }
            revenueChart.getData().add(series);

            // ── Pie chart: orders by status
            ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT status, COUNT(*) AS n FROM orders GROUP BY status")) {
                while (rs.next()) {
                    pie.add(new PieChart.Data(
                            rs.getString("status") + " (" + rs.getInt("n") + ")",
                            rs.getInt("n")));
                }
            }
            statusChart.setData(pie);

            // ── Recent orders table
            ObservableList<RecentRow> rows = FXCollections.observableArrayList();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT o.order_id, CONCAT(u.first_name,' ',u.last_name) AS buyer, " +
                         "       sh.name AS shop, o.total_amount, o.status, o.order_date " +
                         "FROM orders o " +
                         "JOIN users u  ON o.buyer_id = u.user_id " +
                         "JOIN shops sh ON o.shop_id = sh.shop_id " +
                         "ORDER BY o.order_date DESC LIMIT 25")) {
                while (rs.next()) {
                    rows.add(new RecentRow(
                            rs.getInt("order_id"),
                            rs.getString("buyer"),
                            rs.getString("shop"),
                            rs.getDouble("total_amount"),
                            rs.getString("status"),
                            String.valueOf(rs.getTimestamp("order_date"))));
                }
            }
            recentTable.setItems(rows);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @FXML private void goBack() { App.loadScene("AdminPanel.fxml", "Admin Panel"); }
}
