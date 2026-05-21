package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for ProductDetails.fxml
 * Displays images, description, reviews, and Add to Cart.
 */
public class ProductDetailsController implements Initializable {

    // Static "navigation argument" – set before loading this scene
    private static int targetProductId = -1;
    public static void setTargetProductId(int id) { targetProductId = id; }

    @FXML private Label userNameLabel;
    @FXML private Label breadcrumbLabel;
    @FXML private Label productTitleLabel;
    @FXML private Label shopNameLabel;
    @FXML private Label priceLabel;
    @FXML private Label stockLabel;
    @FXML private Label categoryLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label avgRatingLabel;
    @FXML private Label cartFeedbackLabel;
    @FXML private Label imgPlaceholderLabel;
    @FXML private Label imgPlaceholderTextLabel;
    @FXML private ImageView productImageView;
    @FXML private Spinner<Integer> qtySpinner;
    @FXML private VBox  reviewsBox;

    private int currentProductId = -1;
    private int currentStock     = 0;
    private int currentShopId    = -1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) userNameLabel.setText(user.getFullName());

        currentProductId = targetProductId;
        if (currentProductId < 0) {
            productTitleLabel.setText("No product selected.");
            return;
        }
        loadProduct();
        loadReviews();
    }

    /* ── Load product info ────────────────────────────────────────── */
    private void loadProduct() {
        String imageSelect = "NULL AS image_url";
        try (Connection con = DBConnection.getConnection()) {
            if (hasTable(con, "productmedia")) {
                imageSelect = "(SELECT pm.url FROM productmedia pm "
                        + "WHERE pm.product_id = p.product_id AND pm.type = 'image' "
                        + "ORDER BY pm.sort_order, pm.media_id LIMIT 1) AS image_url";
            } else if (hasColumn(con, "products", "image_url")) {
                imageSelect = "p.image_url AS image_url";
            }
        } catch (SQLException e) {
            System.err.println("[ProductDetails] image column: " + e.getMessage());
        }

        String sql =
            "SELECT p.product_id, p.title, p.description, p.price, p.stock_quantity, " +
            "       p.shop_id, c.name AS category_name, s.name AS shop_name, " + imageSelect + " " +
            "FROM   products p " +
            "LEFT JOIN categories c ON p.category_id = c.category_id " +
            "LEFT JOIN shops      s ON p.shop_id      = s.shop_id " +
            "WHERE  p.product_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentProductId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String title = rs.getString("title");
                breadcrumbLabel.setText(title);
                productTitleLabel.setText(title);
                shopNameLabel.setText("by " + rs.getString("shop_name"));
                priceLabel.setText(String.format("$%.2f", rs.getDouble("price")));
                currentStock = rs.getInt("stock_quantity");
                currentShopId = rs.getInt("shop_id");
                stockLabel.setText(currentStock > 0 ? currentStock + " available" : "Out of stock");
                categoryLabel.setText(rs.getString("category_name") != null
                        ? rs.getString("category_name") : "—");
                descriptionLabel.setText(rs.getString("description") != null
                        ? rs.getString("description") : "No description provided.");
                showProductImage(rs.getString("image_url"));

                // Configure spinner max
                SpinnerValueFactory.IntegerSpinnerValueFactory svf =
                        new SpinnerValueFactory.IntegerSpinnerValueFactory(
                                1, Math.max(1, currentStock), 1);
                qtySpinner.setValueFactory(svf);
                qtySpinner.setDisable(currentStock == 0);
            }
        } catch (SQLException e) {
            System.err.println("[ProductDetails] loadProduct: " + e.getMessage());
        }
    }

    private void showProductImage(String imageUrl) {
        boolean hasImage = imageUrl != null && !imageUrl.trim().isEmpty();
        if (hasImage) {
            productImageView.setImage(new Image(imageUrl, true));
        }
        productImageView.setVisible(hasImage);
        productImageView.setManaged(hasImage);
        imgPlaceholderLabel.setVisible(!hasImage);
        imgPlaceholderLabel.setManaged(!hasImage);
        imgPlaceholderTextLabel.setVisible(!hasImage);
        imgPlaceholderTextLabel.setManaged(!hasImage);
    }

    /* ── Load reviews ─────────────────────────────────────────────── */
    private void loadReviews() {
        reviewsBox.getChildren().clear();
        String sql =
            "SELECT r.rating, r.comment, u.first_name, u.last_name " +
            "FROM   productreviews r " +
            "JOIN   users          u ON r.buyer_id = u.user_id " +
            "WHERE  r.product_id = ? " +
            "ORDER BY r.created_at DESC";

        int total = 0; double sum = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentProductId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total++;
                int rating = rs.getInt("rating");
                sum += rating;
                String reviewer = rs.getString("first_name") + " " + rs.getString("last_name");
                String comment   = rs.getString("comment");
                reviewsBox.getChildren().add(buildReviewCard(reviewer, rating, comment));
            }
        } catch (SQLException e) {
            System.err.println("[ProductDetails] loadReviews: " + e.getMessage());
        }

        if (total == 0) {
            avgRatingLabel.setText("");
            Label empty = new Label("No reviews yet.");
            empty.setStyle("-fx-font-size:12px;-fx-text-fill:#aaa;");
            reviewsBox.getChildren().add(empty);
        } else {
            avgRatingLabel.setText(String.format("★ %.1f  (%d review%s)", sum / total, total, total == 1 ? "" : "s"));
        }
    }

    /* ── Build a review card ──────────────────────────────────────── */
    private VBox buildReviewCard(String reviewer, int rating, String comment) {
        VBox card = new VBox(4);
        card.getStyleClass().add("review-card");

        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        Label authorLabel  = new Label(reviewer);    authorLabel.getStyleClass().add("review-author");
        Label ratingLabel  = new Label(stars);       ratingLabel.getStyleClass().add("review-rating");
        Label commentLabel = new Label(comment != null ? comment : ""); commentLabel.getStyleClass().add("review-comment");

        card.getChildren().addAll(authorLabel, ratingLabel, commentLabel);
        return card;
    }

    /* ── Add to Cart ──────────────────────────────────────────────── */
    @FXML private void handleAddToCart() {
        if (currentStock == 0) {
            showFeedback("This product is out of stock.", true);
            return;
        }
        User user = SessionManager.getCurrentUser();
        if (user == null) { App.loadScene("login.fxml", "SparkCraft - Login"); return; }

        try {
            if (resolveSellerUserId(currentShopId) == user.getUserId()) {
                showFeedback("You cannot buy products from your own shop.", true);
                return;
            }
        } catch (SQLException e) {
            showFeedback("Could not verify seller ownership.", true);
            return;
        }
        CartController.ensureCartTable();

        int qty = qtySpinner.getValue();

        // Check if cart item already exists → update, else insert
        String checkSql = "SELECT cart_id, quantity FROM cart WHERE buyer_id = ? AND product_id = ?";
        String insertSql = "INSERT INTO cart (buyer_id, product_id, quantity) VALUES (?, ?, ?)";
        String updateSql = "UPDATE cart SET quantity = ? WHERE cart_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement check = con.prepareStatement(checkSql);
            check.setInt(1, user.getUserId());
            check.setInt(2, currentProductId);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                int cartId   = rs.getInt("cart_id");
                int newQty   = rs.getInt("quantity") + qty;
                PreparedStatement upd = con.prepareStatement(updateSql);
                upd.setInt(1, Math.min(newQty, currentStock));
                upd.setInt(2, cartId);
                upd.executeUpdate();
            } else {
                PreparedStatement ins = con.prepareStatement(insertSql);
                ins.setInt(1, user.getUserId());
                ins.setInt(2, currentProductId);
                ins.setInt(3, qty);
                ins.executeUpdate();
            }
            showFeedback("Added to cart!", false);
        } catch (SQLException e) {
            System.err.println("[ProductDetails] addToCart: " + e.getMessage());
            showFeedback("Error adding to cart.", true);
        }
    }

    @FXML private void handleMessageSeller() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            App.loadScene("login.fxml", "SparkCraft - Login");
            return;
        }
        if (currentShopId <= 0) {
            showFeedback("Seller information is not available.", true);
            return;
        }

        try {
            int sellerId = resolveSellerUserId(currentShopId);
            if (sellerId <= 0) {
                showFeedback("Seller account was not found.", true);
                return;
            }
            if (sellerId == user.getUserId()) {
                showFeedback("This is your own product.", true);
                return;
            }

            int conversationId = MessagingController.findOrCreateConversation(user.getUserId(), sellerId);
            MessagingController.setTargetConversationId(conversationId);
            App.loadScene("Messaging.fxml", "SparkCraft - Messages");
        } catch (SQLException e) {
            System.err.println("[ProductDetails] messageSeller: " + e.getMessage());
            showFeedback("Could not start message.", true);
        }
    }

    private int resolveSellerUserId(int shopId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT owner_id FROM shops WHERE shop_id = ?")) {
            ps.setInt(1, shopId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("owner_id") : 0;
        }
    }

    private boolean hasColumn(Connection con, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, tableName, columnName)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(con.getCatalog(), null, tableName.toUpperCase(), columnName)) {
            return rs.next();
        }
    }

    private boolean hasTable(Connection con, String tableName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet rs = metaData.getTables(con.getCatalog(), null, tableName, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getTables(con.getCatalog(), null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private void showFeedback(String msg, boolean isError) {
        cartFeedbackLabel.setText(msg);
        cartFeedbackLabel.setStyle(isError
                ? "-fx-text-fill:#b8372a;-fx-font-size:12px;"
                : "-fx-text-fill:#1a8a5e;-fx-font-size:12px;");
    }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML private void goBack()          { App.loadScene("ProductListing.fxml", "SparkCraft - Browse"); }
    @FXML private void goBrowseProducts(){ App.loadScene("ProductListing.fxml", "SparkCraft - Browse"); }
    @FXML private void goCart()          { App.loadScene("Cart.fxml",           "SparkCraft - My Cart"); }
    @FXML private void goOrders()        { App.loadScene("MyOrders.fxml", "SparkCraft - My Orders"); }
    @FXML private void goMessages()      { App.loadScene("Messaging.fxml", "SparkCraft - Messages"); }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon"); a.setHeaderText(name);
        a.setContentText("This screen is being built."); a.showAndWait();
    }
}
