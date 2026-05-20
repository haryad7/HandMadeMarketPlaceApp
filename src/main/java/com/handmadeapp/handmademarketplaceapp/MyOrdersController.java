package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;

/**
 * Buyer-side order history for the logged-in user.
 */
public class MyOrdersController {

    public static class OrderRow {
        private final int orderId;
        private final String shop;
        private final double total;
        private final String date;
        private final String status;
        private final String address;

        public OrderRow(int orderId, String shop, double total, String date, String status, String address) {
            this.orderId = orderId;
            this.shop = shop;
            this.total = total;
            this.date = date;
            this.status = status;
            this.address = address;
        }

        public int getOrderId() { return orderId; }
        public String getShop() { return shop; }
        public double getTotal() { return total; }
        public String getDate() { return date; }
        public String getStatus() { return status; }
        public String getAddress() { return address; }
    }

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, Integer> colId;
    @FXML private TableColumn<OrderRow, String> colShop;
    @FXML private TableColumn<OrderRow, Double> colTotal;
    @FXML private TableColumn<OrderRow, String> colDate;
    @FXML private TableColumn<OrderRow, String> colStatus;
    @FXML private TableColumn<OrderRow, String> colAddress;
    @FXML private Label statusMsg;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colShop.setCellValueFactory(new PropertyValueFactory<>("shop"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));

        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "" : String.format("$%.2f", value));
            }
        });

        statusFilter.setItems(FXCollections.observableArrayList(
                "All", "pending", "paid", "shipped", "completed", "cancelled"));
        statusFilter.getSelectionModel().select("All");
        loadOrders();
    }

    @FXML
    public void loadOrders() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            statusMsg.setText("Not logged in.");
            return;
        }

        ObservableList<OrderRow> rows = FXCollections.observableArrayList();
        StringBuilder sql = new StringBuilder(
                "SELECT o.order_id, o.total_amount, o.order_date, o.status, " +
                "       IFNULL(s.name, 'Unknown shop') AS shop_name, " +
                "       CONCAT(IFNULL(a.address_line1,''), ', ', IFNULL(a.city,'')) AS addr " +
                "FROM orders o " +
                "LEFT JOIN shops s ON o.shop_id = s.shop_id " +
                "LEFT JOIN addresses a ON o.shipping_address_id = a.address_id " +
                "WHERE o.buyer_id = ? ");

        String status = statusFilter.getValue();
        if (status != null && !"All".equals(status)) sql.append("AND o.status = ? ");

        String search = searchField.getText() == null ? "" : searchField.getText().trim();
        if (!search.isEmpty()) {
            sql.append("AND (CAST(o.order_id AS CHAR) LIKE ? OR s.name LIKE ?) ");
        }
        sql.append("ORDER BY o.order_date DESC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setInt(idx++, user.getUserId());
            if (status != null && !"All".equals(status)) ps.setString(idx++, status);
            if (!search.isEmpty()) {
                ps.setString(idx++, "%" + search + "%");
                ps.setString(idx++, "%" + search + "%");
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(new OrderRow(
                        rs.getInt("order_id"),
                        rs.getString("shop_name"),
                        rs.getDouble("total_amount"),
                        String.valueOf(rs.getTimestamp("order_date")),
                        rs.getString("status"),
                        rs.getString("addr")));
            }
            ordersTable.setItems(rows);
            statusMsg.setText(rows.size() + " order(s) loaded");
        } catch (SQLException e) {
            statusMsg.setText("Error: " + e.getMessage());
        }
    }

    @FXML private void goBrowseProducts() { App.loadScene("ProductListing.fxml", "SparkCraft - Browse"); }
    @FXML private void goCart()           { App.loadScene("Cart.fxml", "SparkCraft - My Cart"); }
    @FXML private void goMessages()       { App.loadScene("Messaging.fxml", "SparkCraft - Messages"); }
    @FXML private void goDashboard()      { App.loadScene("Dashboard.fxml", "SparkCraft"); }
}
