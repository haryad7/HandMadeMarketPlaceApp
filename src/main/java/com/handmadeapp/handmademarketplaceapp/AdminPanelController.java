package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.sql.*;


public class AdminPanelController {

    /* ─── User row model ─── */
    public static class UserRow {
        private final int    id;
        private final String name, email, role, status;
        public UserRow(int id, String name, String email, String role, String status) {
            this.id = id; this.name = name; this.email = email; this.role = role; this.status = status;
        }
        public int    getId()     { return id; }
        public String getName()   { return name; }
        public String getEmail()  { return email; }
        public String getRole()   { return role; }
        public String getStatus() { return status; }
    }

    /* ─── Product row model ─── */
    public static class ProductRow {
        private final int    id, stock;
        private final String name, shop;
        private final double price;
        public ProductRow(int id, String name, String shop, double price, int stock) {
            this.id = id; this.name = name; this.shop = shop; this.price = price; this.stock = stock;
        }
        public int    getId()    { return id; }
        public String getName()  { return name; }
        public String getShop()  { return shop; }
        public double getPrice() { return price; }
        public int    getStock() { return stock; }
    }

    @FXML private TextField userSearch, productSearch;
    @FXML private Label     userMsg, productMsg;
    @FXML private TabPane   adminTabs;

    private static String initialTab = "users";

    public static void setInitialTab(String tab) {
        initialTab = tab == null ? "users" : tab;
    }

    /* USERS */
    @FXML private TableView<UserRow>             usersTable;
    @FXML private TableColumn<UserRow, Integer>  uColId;
    @FXML private TableColumn<UserRow, String>   uColName, uColEmail, uColRole, uColStatus;
    @FXML private TableColumn<UserRow, Void>     uColAction;

    /* PRODUCTS */
    @FXML private TableView<ProductRow>             productsTable;
    @FXML private TableColumn<ProductRow, Integer>  pColId, pColStock;
    @FXML private TableColumn<ProductRow, String>   pColName, pColShop;
    @FXML private TableColumn<ProductRow, Double>   pColPrice;
    @FXML private TableColumn<ProductRow, Void>     pColAction;

    @FXML
    public void initialize() {
        // ── USERS COLUMNS
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        uColId    .setCellValueFactory(new PropertyValueFactory<>("id"));
        uColName  .setCellValueFactory(new PropertyValueFactory<>("name"));
        uColEmail .setCellValueFactory(new PropertyValueFactory<>("email"));
        uColRole  .setCellValueFactory(new PropertyValueFactory<>("role"));
        uColStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        uColAction.setCellFactory(col -> new TableCell<>() {
            private final Button del    = new Button("Delete");
            private final HBox box      = new HBox(6, del);
            {
                del  .getStyleClass().add("danger-btn");
                del  .setOnAction(e -> deleteUser  (getCurrentRow()));
            }
            private UserRow getCurrentRow() {
                return getTableView().getItems().get(getIndex());
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        // ── PRODUCT COLUMNS
        pColId   .setCellValueFactory(new PropertyValueFactory<>("id"));
        pColName .setCellValueFactory(new PropertyValueFactory<>("name"));
        pColShop .setCellValueFactory(new PropertyValueFactory<>("shop"));
        pColPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        pColStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        pColPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? "" : String.format("$%.2f", v));
            }
        });
        pColAction.setCellFactory(col -> new TableCell<>() {
            private final Button del = new Button("Delete");
            private final HBox box   = new HBox(6, del);
            { del.getStyleClass().add("danger-btn");
              del.setOnAction(e -> deleteProduct(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        loadUsers();
        loadProducts();
        if ("products".equalsIgnoreCase(initialTab)) {
            adminTabs.getSelectionModel().select(1);
        } else {
            adminTabs.getSelectionModel().select(0);
        }
        initialTab = "users";
    }

    /* ────────── USERS ────────── */
    @FXML
    public void loadUsers() {
        ObservableList<UserRow> rows = FXCollections.observableArrayList();
        String q = userSearch.getText() == null ? "" : userSearch.getText().trim();
        boolean hasActiveColumn = hasColumn("users", "is_active");
        String sql = "SELECT user_id, CONCAT(first_name,' ',last_name) AS name, " +
                     "email, role" + (hasActiveColumn ? ", COALESCE(is_active,1) AS active " : " ") +
                     "FROM users " +
                     (q.isEmpty() ? "" : "WHERE username LIKE ? OR email LIKE ? OR first_name LIKE ? ") +
                     "ORDER BY user_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (!q.isEmpty()) {
                ps.setString(1, "%" + q + "%");
                ps.setString(2, "%" + q + "%");
                ps.setString(3, "%" + q + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new UserRow(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("role"),
                            hasActiveColumn
                                    ? (rs.getInt("active") == 1 ? "active" : "banned")
                                    : ("banned".equalsIgnoreCase(rs.getString("role")) ? "banned" : "active")));
                }
            }
            usersTable.setItems(rows);
            userMsg.setText(rows.size() + " user(s)");
        } catch (SQLException ex) {
            userMsg.setText("Error: " + ex.getMessage());
        }
    }

    private void deleteUser(UserRow row) {
        if (!confirm("Delete user #" + row.getId() + " (" + row.getName() + ")?\n"
                + "This will also delete their orders, messages, and shop data.")) return;

        Connection c = null;
        try {
            c = DBConnection.getConnection();
            ensureUsersArchiveCompatible(c);
            c.setAutoCommit(false);

            int uid = row.getId();

            execIfTable(c, "messages",
                    "DELETE m FROM messages m "
                            + "JOIN conversations cv ON m.conversation_id = cv.conversation_id "
                            + "WHERE cv.buyer_id = ? OR cv.seller_id = ?",
                    uid, uid);
            execIfTable(c, "messages",
                    "DELETE FROM messages WHERE sender_id = ?",
                    uid);
            execIfTable(c, "conversations",
                    "DELETE FROM conversations WHERE buyer_id = ? OR seller_id = ?",
                    uid, uid);
            execIfTable(c, "cart",
                    "DELETE c FROM cart c "
                            + "LEFT JOIN products p ON c.product_id = p.product_id "
                            + "LEFT JOIN shops sh ON p.shop_id = sh.shop_id "
                            + "WHERE c.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "payments",
                    "DELETE p FROM payments p "
                            + "JOIN orders o ON p.order_id = o.order_id "
                            + "LEFT JOIN shops sh ON o.shop_id = sh.shop_id "
                            + "WHERE o.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "orderitems",
                    "DELETE oi FROM orderitems oi "
                            + "JOIN orders o ON oi.order_id = o.order_id "
                            + "LEFT JOIN shops sh ON o.shop_id = sh.shop_id "
                            + "WHERE o.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "orderstatushistory",
                    "UPDATE orderstatushistory SET changed_by = NULL WHERE changed_by = ?",
                    uid);
            execIfTable(c, "orders",
                    "DELETE o FROM orders o "
                            + "LEFT JOIN shops sh ON o.shop_id = sh.shop_id "
                            + "WHERE o.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "productreviews",
                    "DELETE pr FROM productreviews pr "
                            + "LEFT JOIN products p ON pr.product_id = p.product_id "
                            + "LEFT JOIN shops sh ON p.shop_id = sh.shop_id "
                            + "WHERE pr.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "sellerreviews",
                    "DELETE sr FROM sellerreviews sr "
                            + "LEFT JOIN shops sh ON sr.shop_id = sh.shop_id "
                            + "WHERE sr.buyer_id = ? OR sh.owner_id = ?",
                    uid, uid);
            execIfTable(c, "products",
                    "DELETE p FROM products p JOIN shops sh ON p.shop_id = sh.shop_id WHERE sh.owner_id = ?",
                    uid);
            execIfTable(c, "shops",
                    "DELETE FROM shops WHERE owner_id = ?",
                    uid);
            execIfTable(c, "addresses",
                    "DELETE FROM addresses WHERE user_id = ?",
                    uid);
            deleteUserRow(c, uid);

            c.commit();
            userMsg.setText("Deleted user #" + row.getId());
            loadUsers();
        } catch (SQLException ex) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            userMsg.setText("Error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try { if (c != null) c.setAutoCommit(true); } catch (SQLException ignored) {}
            try { if (c != null) c.close(); } catch (SQLException ignored) {}
        }
    }

    /* ────────── PRODUCTS ────────── */
    @FXML
    public void loadProducts() {
        ObservableList<ProductRow> rows = FXCollections.observableArrayList();
        String q = productSearch.getText() == null ? "" : productSearch.getText().trim();
        String sql = "SELECT p.product_id, p.title, s.name AS shop, p.price, p.stock_quantity " +
                     "FROM products p LEFT JOIN shops s ON p.shop_id = s.shop_id " +
                     (q.isEmpty() ? "" : "WHERE p.title LIKE ? ") +
                     "ORDER BY p.product_id";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (!q.isEmpty()) ps.setString(1, "%" + q + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ProductRow(
                            rs.getInt("product_id"),
                            rs.getString("title"),
                            rs.getString("shop"),
                            rs.getDouble("price"),
                            rs.getInt("stock_quantity")));
                }
            }
            productsTable.setItems(rows);
            productMsg.setText(rows.size() + " product(s)");
        } catch (SQLException ex) {
            productMsg.setText("Error: " + ex.getMessage());
        }
    }

    private void deleteProduct(ProductRow row) {
        if (!confirm("Delete product #" + row.getId() + " (" + row.getName() + ")?")) return;
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            deleteByProductId(c, "cart", row.getId());
            deleteByProductId(c, "productreviews", row.getId());
            deleteByProductId(c, "orderitems", row.getId());
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM products WHERE product_id = ?")) {
                ps.setInt(1, row.getId());
                ps.executeUpdate();
            }
            c.commit();
            productMsg.setText("Deleted product #" + row.getId());
            loadProducts();
        } catch (SQLException ex) {
            productMsg.setText("Error: " + ex.getMessage());
        }
    }

    /* ─── Helpers ─── */
    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    @FXML private void openReports() { App.loadScene("Reports.fxml",   "Admin — Reports"); }
    @FXML private void goBack()      { App.loadScene("Dashboard.fxml", "Dashboard"); }
    private void deleteByProductId(Connection c, String tableName, int productId) {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + tableName + " WHERE product_id = ?")) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Some schemas may not have all dependent tables yet.
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try (Connection c = DBConnection.getConnection();
             ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean hasColumn(Connection c, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = c.getMetaData();
        try (ResultSet rs = metaData.getColumns(c.getCatalog(), null, tableName, columnName)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(c.getCatalog(), null, tableName.toUpperCase(), columnName)) {
            return rs.next();
        }
    }

    private void execIfTable(Connection c, String tableName, String sql, int... ids) throws SQLException {
        if (hasTable(c, tableName)) {
            exec(c, sql, ids);
        }
    }

    private void exec(Connection c, String sql, int... ids) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                ps.setInt(i + 1, ids[i]);
            }
            ps.executeUpdate();
        }
    }

    private void deleteUserRow(Connection c, int uid) throws SQLException {
        try {
            exec(c, "DELETE FROM users WHERE user_id = ?", uid);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("full_name") || message.contains("deleted_at") || message.contains("role")) {
                ensureUsersArchiveCompatible(c);
                exec(c, "DELETE FROM users WHERE user_id = ?", uid);
                return;
            }
            throw ex;
        }
    }

    private void ensureUsersArchiveCompatible(Connection c) throws SQLException {
        if (!hasTable(c, "users_archive")) return;
        addColumnIfMissing(c, "users_archive", "full_name", "VARCHAR(255) NULL");
        addColumnIfMissing(c, "users_archive", "role", "VARCHAR(50) NULL");
        addColumnIfMissing(c, "users_archive", "deleted_at", "TIMESTAMP NULL DEFAULT NULL");
    }

    private void addColumnIfMissing(Connection c, String tableName, String columnName, String definition) throws SQLException {
        if (!hasColumn(c, tableName, columnName)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            }
        }
    }

    private boolean hasTable(Connection c, String tableName) throws SQLException {
        DatabaseMetaData metaData = c.getMetaData();
        try (ResultSet rs = metaData.getTables(c.getCatalog(), null, tableName, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getTables(c.getCatalog(), null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }
}
