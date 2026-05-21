package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for ProductManagement.fxml
 * Lets a seller add, edit, and delete their products.
 */
public class ProductManagementController implements Initializable {

    /* ── Table ───────────────────────────────────────────────────── */
    @FXML private TableView<ProductRow>     productsTable;
    @FXML private TableColumn<ProductRow, Integer> colId;
    @FXML private TableColumn<ProductRow, String>  colTitle;
    @FXML private TableColumn<ProductRow, String>  colCategory;
    @FXML private TableColumn<ProductRow, Double>  colPrice;
    @FXML private TableColumn<ProductRow, Integer> colStock;
    @FXML private TableColumn<ProductRow, String>  colActive;
    @FXML private TableColumn<ProductRow, Void>    colActions;

    /* ── Side form ───────────────────────────────────────────────── */
    @FXML private Label     userNameLabel;
    @FXML private Label     formTitleLabel;
    @FXML private TextField titleField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private TextArea  descField;
    @FXML private TextField imageUrlField;
    @FXML private CheckBox  activeCheck;
    @FXML private Label     formFeedbackLabel;

    private int shopId        = -1;
    private int editProductId = -1;   // -1 = add mode
    private String imageColumnName = "image_url";

    private final ObservableList<ProductRow> rows = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) userNameLabel.setText(user.getFullName());

        resolveShop();
        ensureProductImageColumn();
        loadCategories();
        setupTable();
        loadProducts();
    }

    /* ── Resolve seller's shop ────────────────────────────────────── */
    private void resolveShop() {
        User user = SessionManager.getCurrentUser();
        if (user == null) return;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT shop_id FROM shops WHERE owner_id = ? LIMIT 1")) {
            ps.setInt(1, user.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) shopId = rs.getInt("shop_id");
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] resolveShop: " + e.getMessage());
        }
    }

    private void ensureProductImageColumn() {
        try (Connection con = DBConnection.getConnection()) {
            if (hasColumn(con, "products", "image_url")) {
                imageColumnName = "image_url";
                return;
            }
            if (hasColumn(con, "products", "imageUrl")) {
                imageColumnName = "imageUrl";
                return;
            }
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE products ADD COLUMN image_url VARCHAR(1024) NULL");
                imageColumnName = "image_url";
            }
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] image column: " + e.getMessage());
        }
    }

    /* ── Load categories ──────────────────────────────────────────── */
    private void loadCategories() {
        categoryCombo.getItems().add("— None —");
        categoryCombo.getSelectionModel().selectFirst();
        try (Connection con = DBConnection.getConnection();
             Statement  st  = con.createStatement();
             ResultSet  rs  = st.executeQuery(
                     "SELECT category_id, name FROM categories ORDER BY name")) {
            while (rs.next())
                categoryCombo.getItems().add(rs.getString("name") + "|" + rs.getInt("category_id"));
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] loadCategories: " + e.getMessage());
        }
    }

    /* ── Setup table columns ──────────────────────────────────────── */
    private void setupTable() {
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : String.format("$%.2f", p));
            }
        });
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colActive.setCellValueFactory(new PropertyValueFactory<>("activeStr"));

        // Actions column: Edit + Delete buttons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            { editBtn.getStyleClass().add("edit-stock-btn");
              deleteBtn.getStyleClass().add("delete-btn");
              editBtn.setOnAction(e -> {
                  ProductRow r = getTableView().getItems().get(getIndex());
                  populateFormForEdit(r);
              });
              deleteBtn.setOnAction(e -> {
                  ProductRow r = getTableView().getItems().get(getIndex());
                  handleDelete(r.getProductId());
              });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, editBtn, deleteBtn);
                setGraphic(box);
            }
        });

        productsTable.setItems(rows);
    }

    /* ── Load products from DB ────────────────────────────────────── */
    private void loadProducts() {
        rows.clear();
        if (shopId < 0) return;
        String sql =
            "SELECT p.product_id, p.title, p.price, p.stock_quantity, p.is_active, " +
            "       c.name AS category_name " +
            "FROM   products p " +
            "LEFT JOIN categories c ON p.category_id = c.category_id " +
            "WHERE  p.shop_id = ? ORDER BY p.created_at DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                rows.add(new ProductRow(
                        rs.getInt("product_id"), rs.getString("title"),
                        rs.getString("category_name"), rs.getDouble("price"),
                        rs.getInt("stock_quantity"), rs.getBoolean("is_active")));
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] load: " + e.getMessage());
        }
    }

    /* ── Populate form for editing ───────────────────────────────── */
    private void populateFormForEdit(ProductRow row) {
        editProductId = row.getProductId();
        formTitleLabel.setText("Edit Product");
        titleField.setText(row.getTitle());
        priceField.setText(String.format("%.2f", row.getPrice()));
        stockField.setText(String.valueOf(row.getStock()));
        activeCheck.setSelected(row.isActive());
        formFeedbackLabel.setText("");

        // Load description + image url from DB
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT description, category_id, " + imageColumnName + " AS image_url FROM products WHERE product_id = ?")) {
            ps.setInt(1, editProductId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                descField.setText(rs.getString("description") != null ? rs.getString("description") : "");
                imageUrlField.setText(rs.getString("image_url") != null ? rs.getString("image_url") : "");
                int catId = rs.getInt("category_id");
                categoryCombo.getItems().stream()
                        .filter(s -> s.contains("|" + catId))
                        .findFirst()
                        .ifPresent(s -> categoryCombo.getSelectionModel().select(s));
            }
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] populateEdit: " + e.getMessage());
        }
    }

    /* ── Save (insert or update) ──────────────────────────────────── */
    @FXML private void handleSave() {
        String title = titleField.getText().trim();
        String priceStr = priceField.getText().trim();
        String stockStr = stockField.getText().trim();

        if (title.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
            showFeedback("Title, price, and stock are required.", true); return;
        }

        double price; int stock;
        try { price = Double.parseDouble(priceStr); stock = Integer.parseInt(stockStr); }
        catch (NumberFormatException e) {
            showFeedback("Price must be a number; Stock must be an integer.", true); return;
        }

        int categoryId = parseCategoryId();
        String desc    = descField.getText().trim();
        String imageUrl = imageUrlField.getText().trim();
        boolean active = activeCheck.isSelected();

        try (Connection con = DBConnection.getConnection()) {
            if (editProductId < 0) {
                // INSERT
                if (shopId < 0) { showFeedback("You need a shop first. Set up My Shop.", true); return; }
                String sql = "INSERT INTO products (shop_id, category_id, title, description, price, stock_quantity, is_active, "
                        + imageColumnName + ") VALUES (?,?,?,?,?,?,?,?)";
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setInt(1, shopId);
                if (categoryId > 0) ps.setInt(2, categoryId); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, title);
                ps.setString(4, desc.isEmpty() ? null : desc);
                ps.setDouble(5, price);
                ps.setInt(6, stock);
                ps.setBoolean(7, active);
                ps.setString(8, imageUrl.isEmpty() ? null : imageUrl);
                ps.executeUpdate();
                showFeedback("Product added!", false);
            } else {
                // UPDATE
                String sql = "UPDATE products SET category_id=?, title=?, description=?, price=?, stock_quantity=?, is_active=?, "
                        + imageColumnName + "=? WHERE product_id=? AND shop_id=?";
                PreparedStatement ps = con.prepareStatement(sql);
                if (categoryId > 0) ps.setInt(1, categoryId); else ps.setNull(1, Types.INTEGER);
                ps.setString(2, title);
                ps.setString(3, desc.isEmpty() ? null : desc);
                ps.setDouble(4, price);
                ps.setInt(5, stock);
                ps.setBoolean(6, active);
                ps.setString(7, imageUrl.isEmpty() ? null : imageUrl);
                ps.setInt(8, editProductId);
                ps.setInt(9, shopId);
                ps.executeUpdate();
                showFeedback("Product updated!", false);
            }
            loadProducts();
            handleClear();
        } catch (SQLException e) {
            System.err.println("[ProductMgmt] save: " + e.getMessage());
            showFeedback("Error: " + e.getMessage(), true);
        }
    }

    /* ── Delete ───────────────────────────────────────────────────── */
    private void handleDelete(int productId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Product");
        confirm.setHeaderText("Delete this product?");
        confirm.setContentText("This action cannot be undone.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "DELETE FROM products WHERE product_id = ? AND shop_id = ?")) {
                    ps.setInt(1, productId); ps.setInt(2, shopId);
                    ps.executeUpdate();
                    loadProducts();
                } catch (SQLException e) {
                    System.err.println("[ProductMgmt] delete: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Product Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));

        File selected = chooser.showOpenDialog(imageUrlField.getScene().getWindow());
        if (selected == null) return;

        try {
            Path imageDir = Path.of(System.getProperty("user.home"), ".sparkcraft", "product-images");
            Files.createDirectories(imageDir);

            String originalName = selected.getName();
            String extension = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) extension = originalName.substring(dot).toLowerCase();

            String fileName = "product-" + System.currentTimeMillis() + extension;
            Path target = imageDir.resolve(fileName);
            Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            imageUrlField.setText(target.toUri().toString());
            showFeedback("Image selected. Save the product to keep it.", false);
        } catch (IOException e) {
            showFeedback("Could not copy image: " + e.getMessage(), true);
        }
    }

    @FXML private void handleAddNew() { handleClear(); formTitleLabel.setText("Add Product"); }

    @FXML private void handleClear() {
        editProductId = -1;
        formTitleLabel.setText("Add Product");
        titleField.clear(); priceField.clear(); stockField.clear();
        descField.clear(); imageUrlField.clear();
        activeCheck.setSelected(true);
        categoryCombo.getSelectionModel().selectFirst();
        formFeedbackLabel.setText("");
    }

    private int parseCategoryId() {
        String sel = categoryCombo.getSelectionModel().getSelectedItem();
        if (sel == null || !sel.contains("|")) return -1;
        try { return Integer.parseInt(sel.split("\\|")[1]); }
        catch (Exception e) { return -1; }
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

    private void showFeedback(String msg, boolean isError) {
        formFeedbackLabel.setText(msg);
        formFeedbackLabel.setStyle(isError
                ? "-fx-text-fill:#b8372a;-fx-font-size:12px;"
                : "-fx-text-fill:#1a8a5e;-fx-font-size:12px;");
    }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML private void goShop()              { App.loadScene("ShopManagement.fxml",   "SparkCraft - My Shop"); }
    @FXML private void goProductManagement() { loadProducts(); }
    @FXML private void goInventory()         { App.loadScene("Inventory.fxml",          "SparkCraft - Inventory"); }
    @FXML private void goOrderManagement()   { App.loadScene("OrderManagement.fxml", "SparkCraft - Order Management"); }
    @FXML private void goMessages()          { App.loadScene("Messaging.fxml",        "SparkCraft - Messages"); }
    @FXML private void goDashboard()         { App.loadScene("Dashboard.fxml",          "SparkCraft"); }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon"); a.setHeaderText(name);
        a.setContentText("This screen is being built."); a.showAndWait();
    }

    /* ════════════════════════════════════════════════════════════
       Inner model class for the TableView
    ════════════════════════════════════════════════════════════ */
    public static class ProductRow {
        private final SimpleIntegerProperty productId;
        private final SimpleStringProperty  title;
        private final SimpleStringProperty  categoryName;
        private final SimpleDoubleProperty  price;
        private final SimpleIntegerProperty stock;
        private final SimpleBooleanProperty active;
        private final SimpleStringProperty  activeStr;

        public ProductRow(int id, String title, String cat, double price, int stock, boolean active) {
            this.productId    = new SimpleIntegerProperty(id);
            this.title        = new SimpleStringProperty(title);
            this.categoryName = new SimpleStringProperty(cat != null ? cat : "—");
            this.price        = new SimpleDoubleProperty(price);
            this.stock        = new SimpleIntegerProperty(stock);
            this.active       = new SimpleBooleanProperty(active);
            this.activeStr    = new SimpleStringProperty(active ? "Yes" : "No");
        }

        public int    getProductId()   { return productId.get(); }
        public String getTitle()       { return title.get(); }
        public String getCategoryName(){ return categoryName.get(); }
        public double getPrice()       { return price.get(); }
        public int    getStock()       { return stock.get(); }
        public boolean isActive()      { return active.get(); }
        public String getActiveStr()   { return activeStr.get(); }
    }
}
