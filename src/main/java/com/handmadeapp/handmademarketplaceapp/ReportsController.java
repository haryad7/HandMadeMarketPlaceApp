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
 * + pie chart (orders by status) + shop performance table.
 */
public class ReportsController {

    public static class ShopStatRow {
        private final String shop;
        private final String status;
        private final double revenue;
        private final double avgOrder;
        private final int orders;
        private final int itemsSold;
        private final int pending;

        public ShopStatRow(String shop, double revenue, int orders,
                           double avgOrder, int itemsSold, int pending, String status) {
            this.shop = shop;
            this.revenue = revenue;
            this.orders = orders;
            this.avgOrder = avgOrder;
            this.itemsSold = itemsSold;
            this.pending = pending;
            this.status = status;
        }

        public String getShop()     { return shop; }
        public double getRevenue()  { return revenue; }
        public int    getOrders()   { return orders; }
        public double getAvgOrder() { return avgOrder; }
        public int    getItemsSold(){ return itemsSold; }
        public int    getPending()  { return pending; }
        public String getStatus()   { return status; }
    }

    @FXML private Label     revenueLabel, ordersLabel, paidLabel, usersLabel;
    @FXML private BarChart<String, Number> revenueChart;
    @FXML private CategoryAxis revenueX;
    @FXML private NumberAxis   revenueY;
    @FXML private PieChart    statusChart;
    @FXML private TableView<ShopStatRow> shopStatsTable;
    @FXML private TableColumn<ShopStatRow, String>  sColShop, sColStatus;
    @FXML private TableColumn<ShopStatRow, Double>  sColRevenue, sColAvgOrder;
    @FXML private TableColumn<ShopStatRow, Integer> sColOrders, sColItems, sColPending;

    @FXML
    public void initialize() {
        shopStatsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        sColShop    .setCellValueFactory(new PropertyValueFactory<>("shop"));
        sColRevenue .setCellValueFactory(new PropertyValueFactory<>("revenue"));
        sColOrders  .setCellValueFactory(new PropertyValueFactory<>("orders"));
        sColAvgOrder.setCellValueFactory(new PropertyValueFactory<>("avgOrder"));
        sColItems   .setCellValueFactory(new PropertyValueFactory<>("itemsSold"));
        sColPending .setCellValueFactory(new PropertyValueFactory<>("pending"));
        sColStatus  .setCellValueFactory(new PropertyValueFactory<>("status"));

        sColRevenue.setCellFactory(c -> currencyCell());
        sColAvgOrder.setCellFactory(c -> currencyCell());

        loadAll();
    }

    private TableCell<ShopStatRow, Double> currencyCell() {
        return new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? "" : String.format("$%,.2f", v));
            }
        };
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
            ObservableList<ShopStatRow> rows = FXCollections.observableArrayList();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT sh.name AS shop, sh.status, " +
                         "  COALESCE(SUM(CASE WHEN o.status IN ('paid','shipped','completed') THEN o.total_amount END), 0) AS revenue, " +
                         "  COUNT(DISTINCT o.order_id) AS orders_total, " +
                         "  COALESCE(AVG(CASE WHEN o.status IN ('paid','shipped','completed') THEN o.total_amount END), 0) AS avg_order, " +
                         "  COALESCE(SUM(CASE WHEN o.status IN ('paid','shipped','completed') THEN oi.quantity END), 0) AS items_sold, " +
                         "  COUNT(DISTINCT CASE WHEN o.status = 'pending' THEN o.order_id END) AS pending_count " +
                         "FROM shops sh " +
                         "LEFT JOIN orders o ON sh.shop_id = o.shop_id " +
                         "LEFT JOIN orderitems oi ON o.order_id = oi.order_id " +
                         "GROUP BY sh.shop_id, sh.name, sh.status " +
                         "ORDER BY revenue DESC")) {
                while (rs.next()) {
                    rows.add(new ShopStatRow(
                            rs.getString("shop"),
                            rs.getDouble("revenue"),
                            rs.getInt("orders_total"),
                            rs.getDouble("avg_order"),
                            rs.getInt("items_sold"),
                            rs.getInt("pending_count"),
                            rs.getString("status")
                    ));
                }
            }
            shopStatsTable.setItems(rows);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @FXML private void goBack() { App.loadScene("AdminPanel.fxml", "Admin Panel"); }
}
