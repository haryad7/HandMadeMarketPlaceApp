package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Step 2 of checkout: choose payment method, create the order, persist cart
 * items into orderitems, then jump to OrderConfirmation.
 */
public class PaymentController {

    @FXML private ComboBox<String> methodBox;
    @FXML private TextField cardholderField, cardNumberField;
    @FXML private ComboBox<Integer> expMonthBox, expYearBox;
    @FXML private PasswordField cvvField;
    @FXML private VBox cardDetailsBox;
    @FXML private Label errorLabel, subtotalLine, shippingLine, totalLine;

    @FXML
    public void initialize() {
        methodBox.setItems(FXCollections.observableArrayList("credit_card", "cash_on_delivery"));
        methodBox.getSelectionModel().select("credit_card");

        for (int m = 1; m <= 12; m++) expMonthBox.getItems().add(m);
        int year = LocalDate.now().getYear();
        for (int y = year; y <= year + 12; y++) expYearBox.getItems().add(y);

        double subtotal = CheckoutContext.getCartTotal();
        double shipping = subtotal > 0 ? 5.00 : 0.00;
        double total = subtotal + shipping;
        subtotalLine.setText(String.format("Subtotal: $%.2f", subtotal));
        shippingLine.setText(String.format("Shipping: $%.2f", shipping));
        totalLine.setText(String.format("$%.2f", total));
        handleMethodChange();
    }

    @FXML
    private void handleMethodChange() {
        boolean needsCard = isCardPayment();
        cardDetailsBox.setVisible(needsCard);
        cardDetailsBox.setManaged(needsCard);
        if (!needsCard) {
            cardholderField.clear();
            cardNumberField.clear();
            expMonthBox.getSelectionModel().clearSelection();
            expYearBox.getSelectionModel().clearSelection();
            cvvField.clear();
            errorLabel.setText("");
        }
    }

    @FXML
    private void handlePay() {
        if (!validate()) return;

        User user = SessionManager.getCurrentUser();
        if (user == null) {
            errorLabel.setText("Not logged in.");
            return;
        }

        double subtotal = CheckoutContext.getCartTotal();
        if (subtotal <= 0) {
            errorLabel.setText("Your cart is empty.");
            return;
        }

        double shipping = 5.00;
        double total = subtotal + shipping;
        int shopId = CheckoutContext.getShopId();
        int addressId = CheckoutContext.getShippingAddressId();
        String method = methodBox.getValue();
        boolean cashOnDelivery = "cash_on_delivery".equals(method);
        String orderStatus = cashOnDelivery ? "pending" : "paid";
        String paymentStatus = cashOnDelivery ? "pending" : "completed";

        CartController.ensureCartTable();
        ensureOrderItemsTable();

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int orderId = createOrder(con, user.getUserId(), shopId, total, orderStatus, addressId);
            int itemCount = createOrderItemsFromCart(con, orderId, user.getUserId());
            if (itemCount == 0) {
                con.rollback();
                errorLabel.setText("No cart items found for this order.");
                return;
            }
            int paymentId = createPayment(con, orderId, total, method, paymentStatus, cashOnDelivery);
            clearCart(con, user.getUserId());

            con.commit();

            CheckoutContext.setOrderId(orderId);
            CheckoutContext.setPaymentId(paymentId);
            App.loadScene("OrderConfirmation.fxml", "Order Confirmed");
        } catch (SQLException e) {
            try { if (con != null) con.rollback(); } catch (SQLException ignored) {}
            errorLabel.setText("Payment failed: " + e.getMessage());
        } finally {
            try { if (con != null) con.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @FXML
    private void goBack() {
        App.loadScene("Order.fxml", "Checkout - Shipping");
    }

    private int createOrder(Connection con, int userId, int shopId, double total,
                            String status, int addressId) throws SQLException {
        String sql = "INSERT INTO orders (buyer_id, shop_id, total_amount, status, shipping_address_id) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, shopId > 0 ? shopId : 1);
            ps.setDouble(3, total);
            ps.setString(4, status);
            ps.setInt(5, addressId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) throw new SQLException("Could not create order.");
            return keys.getInt(1);
        }
    }

    private int createOrderItemsFromCart(Connection con, int orderId, int userId) throws SQLException {
        String selectSql = "SELECT c.product_id, c.quantity, p.price "
                + "FROM cart c JOIN products p ON c.product_id = p.product_id "
                + "WHERE c.buyer_id = ?";
        Set<String> columns = getTableColumns(con, "orderitems");
        String priceColumn = columns.contains("unit_price") ? "unit_price"
                : columns.contains("price") ? "price"
                : columns.contains("unitPrice") ? "unitPrice"
                : null;
        String subtotalColumn = columns.contains("subtotal") ? "subtotal"
                : columns.contains("total_price") ? "total_price"
                : null;

        List<String> insertColumns = new ArrayList<>();
        insertColumns.add("order_id");
        insertColumns.add("product_id");
        insertColumns.add("quantity");
        if (priceColumn != null) insertColumns.add(priceColumn);
        if (subtotalColumn != null) insertColumns.add(subtotalColumn);

        String insertSql = "INSERT INTO orderitems (" + String.join(", ", insertColumns) + ") VALUES ("
                + "?,".repeat(insertColumns.size()).replaceAll(",$", "") + ")";

        int count = 0;
        try (PreparedStatement select = con.prepareStatement(selectSql);
             PreparedStatement insert = con.prepareStatement(insertSql)) {
            select.setInt(1, userId);
            ResultSet rs = select.executeQuery();
            while (rs.next()) {
                int qty = rs.getInt("quantity");
                double price = rs.getDouble("price");
                int idx = 1;
                insert.setInt(idx++, orderId);
                insert.setInt(idx++, rs.getInt("product_id"));
                insert.setInt(idx++, qty);
                if (priceColumn != null) insert.setDouble(idx++, price);
                if (subtotalColumn != null) insert.setDouble(idx++, price * qty);
                insert.addBatch();
                count++;
            }
            if (count > 0) insert.executeBatch();
        }
        return count;
    }

    private int createPayment(Connection con, int orderId, double total, String method,
                              String status, boolean cashOnDelivery) throws SQLException {
        String sql = cashOnDelivery
                ? "INSERT INTO payments (order_id, amount, method, status, transaction_id) VALUES (?,?,?,?,?)"
                : "INSERT INTO payments (order_id, amount, method, status, transaction_id, paid_at) VALUES (?,?,?,?,?,NOW())";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setDouble(2, total);
            ps.setString(3, method);
            ps.setString(4, status);
            ps.setString(5, (cashOnDelivery ? "COD-" : "TXN-")
                    + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : 0;
        }
    }

    private void clearCart(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM cart WHERE buyer_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private boolean validate() {
        if (!isCardPayment()) {
            errorLabel.setText("");
            return true;
        }

        String num = cardNumberField.getText().replaceAll("\\s+", "");
        if (cardholderField.getText().trim().isEmpty()) {
            errorLabel.setText("Enter the cardholder name.");
            return false;
        }
        if (!num.matches("\\d{13,19}")) {
            errorLabel.setText("Enter a valid card number (13-19 digits).");
            return false;
        }
        if (expMonthBox.getValue() == null || expYearBox.getValue() == null) {
            errorLabel.setText("Choose expiry month and year.");
            return false;
        }
        if (!cvvField.getText().matches("\\d{3,4}")) {
            errorLabel.setText("CVV must be 3 or 4 digits.");
            return false;
        }
        errorLabel.setText("");
        return true;
    }

    private boolean isCardPayment() {
        return "credit_card".equals(methodBox.getValue());
    }

    private void ensureOrderItemsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS orderitems ("
                + "order_item_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "order_id INT NOT NULL, "
                + "product_id INT NOT NULL, "
                + "quantity INT NOT NULL, "
                + "unit_price DECIMAL(10,2) NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            System.err.println("[Payment] ensureOrderItemsTable: " + e.getMessage());
        }
    }

    private Set<String> getTableColumns(Connection con, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, tableName, null)) {
            while (rs.next()) columns.add(rs.getString("COLUMN_NAME"));
        }
        if (columns.isEmpty()) {
            try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, tableName.toUpperCase(), null)) {
                while (rs.next()) columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }
}
