package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Seller-side: view incoming orders for the logged-in user's shop(s)
 * and update their status (pending → paid → shipped → completed).
 */
public class OrderManagementController {

    public static class OrderRow {
        private final int    orderId;
        private final String buyer;
        private final String seller;
        private final double total;
        private final String date;
        private final String status;
        private final String address;
        public OrderRow(int orderId, String buyer, String seller, double total,
                        String date, String status, String address) {
            this.orderId = orderId; this.buyer = buyer; this.seller = seller; this.total = total;
            this.date = date; this.status = status; this.address = address;
        }
        public int    getOrderId() { return orderId; }
        public String getBuyer()   { return buyer; }
        public String getSeller()  { return seller; }
        public double getTotal()   { return total; }
        public String getDate()    { return date; }
        public String getStatus()  { return status; }
        public String getAddress() { return address; }
    }

    @FXML private TextField           searchField;
    @FXML private ComboBox<String>    statusFilter, newStatusBox;
    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, Integer> colId;
    @FXML private TableColumn<OrderRow, String>  colBuyer, colSeller, colDate, colStatus, colAddress;
    @FXML private TableColumn<OrderRow, Double>  colTotal;
    @FXML private Label statusMsg;

    @FXML
    public void initialize() {
        colId     .setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colBuyer  .setCellValueFactory(new PropertyValueFactory<>("buyer"));
        if (colSeller != null) colSeller.setCellValueFactory(new PropertyValueFactory<>("seller"));
        colTotal  .setCellValueFactory(new PropertyValueFactory<>("total"));
        colDate   .setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus .setCellValueFactory(new PropertyValueFactory<>("status"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));

        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? "" : String.format("$%.2f", v));
            }
        });

        statusFilter.setItems(FXCollections.observableArrayList(
                "All", "pending", "paid", "shipped", "completed", "cancelled"));
        statusFilter.getSelectionModel().select("All");

        newStatusBox.setItems(FXCollections.observableArrayList(
                "pending", "paid", "shipped", "completed", "cancelled"));
        newStatusBox.getSelectionModel().select("shipped");

        loadOrders();
    }

    @FXML
    public void loadOrders() {
        User u = SessionManager.getCurrentUser();
        if (u == null) { statusMsg.setText("Not logged in."); return; }

        ObservableList<OrderRow> rows = FXCollections.observableArrayList();
        boolean isAdmin = "admin".equalsIgnoreCase(u.getRole());

        StringBuilder sql = new StringBuilder(
            "SELECT o.order_id, o.total_amount, o.order_date, o.status, " +
            "       CONCAT(u.first_name,' ',u.last_name) AS buyer, " +
            "       CONCAT(su.first_name,' ',su.last_name) AS seller, " +
            "       CONCAT(IFNULL(a.address_line1,''), ', ', IFNULL(a.city,'')) AS addr " +
            "FROM orders o " +
            "JOIN users u  ON o.buyer_id = u.user_id " +
            "LEFT JOIN addresses a ON o.shipping_address_id = a.address_id " +
            "JOIN shops s ON o.shop_id = s.shop_id " +
            "LEFT JOIN users su ON s.owner_id = su.user_id " +
            (isAdmin ? "WHERE 1=1 " : "WHERE s.owner_id = ? ")
        );

        String statusF = statusFilter.getValue();
        if (statusF != null && !"All".equals(statusF)) sql.append("AND o.status = ? ");

        String search = searchField.getText() == null ? "" : searchField.getText().trim();
        if (!search.isEmpty()) sql.append(
                "AND (CAST(o.order_id AS CHAR) LIKE ? " +
                "  OR CONCAT(u.first_name,' ',u.last_name) LIKE ? " +
                "  OR CONCAT(su.first_name,' ',su.last_name) LIKE ?) "
        );

        sql.append("ORDER BY o.order_date DESC");

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = 1;
            if (!isAdmin) ps.setInt(idx++, u.getUserId());
            if (statusF != null && !"All".equals(statusF)) ps.setString(idx++, statusF);
            if (!search.isEmpty()) {
                ps.setString(idx++, "%" + search + "%");
                ps.setString(idx++, "%" + search + "%");
                ps.setString(idx++, "%" + search + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OrderRow(
                            rs.getInt   ("order_id"),
                            rs.getString("buyer"),
                            rs.getString("seller"),
                            rs.getDouble("total_amount"),
                            String.valueOf(rs.getTimestamp("order_date")),
                            rs.getString("status"),
                            rs.getString("addr")));
                }
            }
            ordersTable.setItems(rows);
            statusMsg.setText(rows.size() + " order(s) loaded");
        } catch (SQLException ex) {
            statusMsg.setText("Error: " + ex.getMessage());
        }
    }

    @FXML
    public void updateStatus() {
        OrderRow sel = ordersTable.getSelectionModel().getSelectedItem();
        if (sel == null)              { statusMsg.setText("Select an order first.");  return; }
        String next = newStatusBox.getValue();
        if (next == null)             { statusMsg.setText("Pick a new status.");      return; }

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE orders SET status = ? WHERE order_id = ?")) {
            ps.setString(1, next);
            ps.setInt   (2, sel.getOrderId());
            int n = ps.executeUpdate();
            statusMsg.setText(n > 0
                    ? "Order #" + sel.getOrderId() + " → " + next
                    : "No row updated.");
            loadOrders();
        } catch (SQLException ex) {
            statusMsg.setText("Error: " + ex.getMessage());
        }
    }

    @FXML private void goBack() { App.loadScene("Dashboard.fxml", "Dashboard"); }
}
