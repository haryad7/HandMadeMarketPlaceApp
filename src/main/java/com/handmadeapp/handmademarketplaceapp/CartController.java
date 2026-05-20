package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for Cart.fxml
 * Lists cart items with quantity controls and shows live subtotal.
 *
 * IMPORTANT – run this SQL once to create the cart table if it does not exist:
 * <pre>
 * CREATE TABLE IF NOT EXISTS `cart` (
 *   `cart_id`    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
 *   `buyer_id`   INT          NOT NULL,
 *   `product_id` INT          NOT NULL,
 *   `quantity`   INT          NOT NULL DEFAULT 1,
 *   `added_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   UNIQUE KEY `uq_buyer_product` (`buyer_id`, `product_id`)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 * </pre>
 */
public class CartController implements Initializable {

    @FXML private Label userNameLabel;
    @FXML private VBox  cartItemsBox;
    @FXML private Label itemCountLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label totalLabel;
    @FXML private Label checkoutFeedbackLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) userNameLabel.setText(user.getFullName());
        loadCart();
    }

    /* ── Load cart items ──────────────────────────────────────────── */
    private void loadCart() {
        cartItemsBox.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        if (user == null) return;

        String sql =
            "SELECT c.cart_id, c.quantity, p.product_id, p.title, p.price, " +
            "       p.stock_quantity, s.name AS shop_name " +
            "FROM   cart c " +
            "JOIN   products p ON c.product_id = p.product_id " +
            "LEFT JOIN shops s ON p.shop_id    = s.shop_id " +
            "WHERE  c.buyer_id = ? " +
            "ORDER BY c.cart_id";

        double subtotal = 0;
        int    itemCount = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, user.getUserId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                itemCount++;
                int    cartId   = rs.getInt("cart_id");
                int    qty      = rs.getInt("quantity");
                int    maxStock = rs.getInt("stock_quantity");
                double price    = rs.getDouble("price");
                String title    = rs.getString("title");
                String shop     = rs.getString("shop_name");

                subtotal += price * qty;
                cartItemsBox.getChildren().add(buildCartRow(cartId, title, shop, price, qty, maxStock));
            }
        } catch (SQLException e) {
            System.err.println("[Cart] loadCart: " + e.getMessage());
        }

        itemCountLabel.setText(String.valueOf(itemCount));
        subtotalLabel.setText(String.format("$%.2f", subtotal));
        totalLabel.setText(String.format("$%.2f", subtotal));

        if (itemCount == 0) {
            Label empty = new Label("Your cart is empty. Start browsing!");
            empty.setStyle("-fx-font-size:14px;-fx-text-fill:#aaa;-fx-padding:40;");
            cartItemsBox.getChildren().add(empty);
        }
    }

    /* ── Build a single cart row ──────────────────────────────────── */
    private HBox buildCartRow(int cartId, String title, String shop,
                               double price, int qty, int maxStock) {
        HBox row = new HBox(16);
        row.getStyleClass().add("cart-item-row");
        row.setAlignment(Pos.CENTER_LEFT);

        // Info block
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label titleLabel = new Label(title); titleLabel.getStyleClass().add("cart-item-title");
        Label shopLabel  = new Label("from " + shop); shopLabel.getStyleClass().add("cart-item-shop");
        Label unitPrice  = new Label(String.format("$%.2f each", price));
        unitPrice.setStyle("-fx-font-size:11px;-fx-text-fill:#888;");
        info.getChildren().addAll(titleLabel, shopLabel, unitPrice);

        // Qty controls
        HBox qtyBox = new HBox(6);
        qtyBox.setAlignment(Pos.CENTER);
        Button minus = new Button("−");
        Button plus  = new Button("+");
        Label  qtyLabel = new Label(String.valueOf(qty));
        qtyLabel.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-min-width:28;-fx-alignment:center;");
        for (Button b : new Button[]{minus, plus}) {
            b.setStyle("-fx-background-color:#1d2d6b;-fx-text-fill:white;-fx-font-weight:bold;" +
                       "-fx-background-radius:4;-fx-cursor:hand;-fx-padding:4 10 4 10;");
        }
        qtyBox.getChildren().addAll(minus, qtyLabel, plus);

        // Line total
        Label lineTotal = new Label(String.format("$%.2f", price * qty));
        lineTotal.getStyleClass().add("cart-item-price");
        lineTotal.setMinWidth(80);
        lineTotal.setAlignment(Pos.CENTER_RIGHT);

        // Remove button
        Button removeBtn = new Button("✕ Remove");
        removeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#b8372a;" +
                           "-fx-font-size:12px;-fx-cursor:hand;-fx-border-width:0;");

        // Wire qty controls
        minus.setOnAction(e -> {
            int cur = Integer.parseInt(qtyLabel.getText());
            if (cur > 1) { updateCartQty(cartId, cur - 1); loadCart(); }
        });
        plus.setOnAction(e -> {
            int cur = Integer.parseInt(qtyLabel.getText());
            if (cur < maxStock) { updateCartQty(cartId, cur + 1); loadCart(); }
        });
        removeBtn.setOnAction(e -> { deleteCartItem(cartId); loadCart(); });

        row.getChildren().addAll(info, qtyBox, lineTotal, removeBtn);
        return row;
    }

    /* ── DB helpers ───────────────────────────────────────────────── */
    private void updateCartQty(int cartId, int newQty) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE cart SET quantity = ? WHERE cart_id = ?")) {
            ps.setInt(1, newQty);
            ps.setInt(2, cartId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Cart] updateQty: " + e.getMessage());
        }
    }

    private void deleteCartItem(int cartId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM cart WHERE cart_id = ?")) {
            ps.setInt(1, cartId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Cart] delete: " + e.getMessage());
        }
    }

    /* ── Checkout placeholder ─────────────────────────────────────── */
    @FXML private void handleCheckout() {
        checkoutFeedbackLabel.setText("Checkout flow coming soon!");
        checkoutFeedbackLabel.setStyle("-fx-text-fill:#c47a15;-fx-font-size:12px;");
    }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML private void goBrowseProducts() { App.loadScene("ProductListing.fxml", "SparkCraft - Browse"); }
    @FXML private void goCart()           { loadCart(); /* already here */ }
    @FXML private void goOrders()         { comingSoon("My Orders"); }
    @FXML private void goMessages()       { comingSoon("Messages"); }
    @FXML private void goDashboard()      { App.loadScene("Dashboard.fxml", "SparkCraft"); }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon"); a.setHeaderText(name);
        a.setContentText("This screen is being built."); a.showAndWait();
    }
}
