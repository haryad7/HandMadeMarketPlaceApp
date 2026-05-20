package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for ProductListing.fxml
 * Displays a grid of product cards with search and category filter.
 */
public class ProductListingController implements Initializable {

    @FXML private Label       userNameLabel;
    @FXML private TextField   searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private Label       resultCountLabel;
    @FXML private FlowPane    productGrid;

    // Holds the currently selected category_id (0 = all)
    private int selectedCategoryId = 0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) userNameLabel.setText(user.getFullName());

        loadCategories();
        loadProducts("", 0);
    }

    /* ── Load categories into the ComboBox ─────────────────────────── */
    private void loadCategories() {
        categoryFilter.getItems().add("All Categories");
        categoryFilter.getSelectionModel().selectFirst();

        String sql = "SELECT category_id, name FROM categories WHERE parent_id IS NULL ORDER BY name";
        try (Connection con = DBConnection.getConnection();
             Statement  st  = con.createStatement();
             ResultSet  rs  = st.executeQuery(sql)) {

            while (rs.next()) {
                // Store "name|id" so we can split later
                categoryFilter.getItems().add(rs.getString("name") + "|" + rs.getInt("category_id"));
            }
        } catch (SQLException e) {
            System.err.println("[ProductListing] loadCategories: " + e.getMessage());
        }
    }

    /* ── Load / refresh product cards ─────────────────────────────── */
    private void loadProducts(String keyword, int categoryId) {
        productGrid.getChildren().clear();

        StringBuilder sql = new StringBuilder(
            "SELECT p.product_id, p.title, p.price, p.stock_quantity, " +
            "       c.name AS category_name, s.name AS shop_name " +
            "FROM   products p " +
            "LEFT JOIN categories c ON p.category_id = c.category_id " +
            "LEFT JOIN shops      s ON p.shop_id      = s.shop_id " +
            "WHERE  p.is_active = 1 ");

        if (!keyword.isBlank()) sql.append("AND p.title LIKE ? ");
        if (categoryId > 0)     sql.append("AND (p.category_id = ? OR c.parent_id = ?) ");
        sql.append("ORDER BY p.created_at DESC");

        int count = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            int idx = 1;
            if (!keyword.isBlank()) ps.setString(idx++, "%" + keyword + "%");
            if (categoryId > 0)   { ps.setInt(idx++, categoryId); ps.setInt(idx++, categoryId); }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                count++;
                productGrid.getChildren().add(buildProductCard(
                    rs.getInt("product_id"),
                    rs.getString("title"),
                    rs.getString("shop_name"),
                    rs.getString("category_name"),
                    rs.getDouble("price"),
                    rs.getInt("stock_quantity")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[ProductListing] loadProducts: " + e.getMessage());
        }

        resultCountLabel.setText(count + " product" + (count == 1 ? "" : "s") + " found");
    }

    /* ── Build a single product card VBox ─────────────────────────── */
    private VBox buildProductCard(int productId, String title, String shopName,
                                   String category, double price, int stock) {
        VBox card = new VBox(8);
        card.getStyleClass().add("product-card");
        card.setPrefWidth(200);
        card.setStyle("-fx-padding: 16;");

        // Category badge
        Label catLabel = new Label(category != null ? category : "Uncategorized");
        catLabel.getStyleClass().add("product-card-category");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("product-card-title");
        titleLabel.setWrapText(true);

        Label shopLabel = new Label("by " + (shopName != null ? shopName : "Unknown"));
        shopLabel.getStyleClass().add("product-card-shop");

        Label priceLabel = new Label(String.format("$%.2f", price));
        priceLabel.getStyleClass().add("product-card-price");

        Label stockLabel = new Label(stock > 0 ? stock + " in stock" : "Out of stock");
        stockLabel.setStyle(stock > 0 ? "-fx-text-fill:#1a8a5e;-fx-font-size:11px;"
                                      : "-fx-text-fill:#b8372a;-fx-font-size:11px;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button viewBtn = new Button("View Details");
        viewBtn.getStyleClass().add("view-btn");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setOnAction(e -> openProductDetails(productId));

        card.getChildren().addAll(catLabel, titleLabel, shopLabel, priceLabel, stockLabel, spacer, viewBtn);
        return card;
    }

    /* ── Open product details screen ──────────────────────────────── */
    private void openProductDetails(int productId) {
        ProductDetailsController.setTargetProductId(productId);
        App.loadScene("ProductDetails.fxml", "SparkCraft - Product Details");
    }

    /* ── FXML handlers ────────────────────────────────────────────── */
    @FXML private void handleSearch() {
        loadProducts(searchField.getText().trim(), selectedCategoryId);
    }

    @FXML private void handleCategoryFilter() {
        String selected = categoryFilter.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals("All Categories")) {
            selectedCategoryId = 0;
        } else {
            try { selectedCategoryId = Integer.parseInt(selected.split("\\|")[1]); }
            catch (Exception ex) { selectedCategoryId = 0; }
        }
        loadProducts(searchField.getText().trim(), selectedCategoryId);
    }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML private void goBrowseProducts() { App.loadScene("ProductListing.fxml", "SparkCraft - Browse"); }
    @FXML private void goCart()            { App.loadScene("Cart.fxml",           "SparkCraft - My Cart"); }
    @FXML private void goOrders()          { comingSoon("My Orders"); }
    @FXML private void goMessages()        { comingSoon("Messages"); }
    @FXML private void goDashboard()       { App.loadScene("Dashboard.fxml",      "SparkCraft"); }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon"); a.setHeaderText(name);
        a.setContentText("This screen is being built."); a.showAndWait();
    }
}
