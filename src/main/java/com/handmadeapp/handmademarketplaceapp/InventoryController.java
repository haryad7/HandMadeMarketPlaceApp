package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for Inventory.fxml Shows stock levels with colour-coded status and
 * inline quantity editing.
 */
public class InventoryController implements Initializable {

    @FXML
    private Label userNameLabel;
    @FXML
    private Spinner<Integer> thresholdSpinner;
    @FXML
    private Label totalProductsLabel;
    @FXML
    private Label inStockLabel;
    @FXML
    private Label lowStockLabel;
    @FXML
    private Label outOfStockLabel;

    @FXML
    private TableView<InvRow> inventoryTable;
    @FXML
    private TableColumn<InvRow, Integer> colId;
    @FXML
    private TableColumn<InvRow, String> colTitle;
    @FXML
    private TableColumn<InvRow, String> colCat;
    @FXML
    private TableColumn<InvRow, Integer> colStock;
    @FXML
    private TableColumn<InvRow, String> colStatus;
    @FXML
    private TableColumn<InvRow, Void> colEdit;

    private int shopId = -1;
    private int threshold = 5;

    private final ObservableList<InvRow> rows = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            userNameLabel.setText(user.getFullName());
        }

        thresholdSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, threshold));
        thresholdSpinner.valueProperty().addListener((obs, oldValue, newValue) -> loadInventory());

        resolveShop();
        setupTable();
        loadInventory();
    }

    private void resolveShop() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            return;
        }
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(
                "SELECT shop_id FROM shops WHERE owner_id = ? LIMIT 1")) {
            ps.setInt(1, user.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                shopId = rs.getInt("shop_id");
            }
        } catch (SQLException e) {
            System.err.println("[Inventory] resolveShop: " + e.getMessage());
        }
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colCat.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));

        // Status cell with colour
        colStatus.setCellValueFactory(new PropertyValueFactory<>("statusStr"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(s);
                getStyleClass().removeAll("stock-ok", "stock-low", "stock-out");
                switch (s) {
                    case "In Stock":
                        getStyleClass().add("stock-ok");
                        break;
                    case "Low Stock":
                        getStyleClass().add("stock-low");
                        break;
                    case "Out of Stock":
                        getStyleClass().add("stock-out");
                        break;
                }
            }
        });

        // Edit (update qty) column
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Update Stock");

            {
                btn.getStyleClass().add("edit-stock-btn");
                btn.setOnAction(e -> {
                    InvRow row = getTableView().getItems().get(getIndex());
                    showStockEditor(row);
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        inventoryTable.setItems(rows);
    }

    @FXML
    public void loadInventory() {
        rows.clear();
        threshold = thresholdSpinner.getValue();
        if (shopId < 0) {
            return;
        }

        String sql
                = "SELECT p.product_id, p.title, p.stock_quantity, c.name AS category_name "
                + "FROM   products p "
                + "LEFT JOIN categories c ON p.category_id = c.category_id "
                + "WHERE  p.shop_id = ? ORDER BY p.stock_quantity ASC, p.title";

        int total = 0, inStock = 0, low = 0, out = 0;
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total++;
                int stock = rs.getInt("stock_quantity");
                if (stock == 0) {
                    out++;
                } else if (stock <= threshold) {
                    low++;
                } else {
                    inStock++;
                }
                rows.add(new InvRow(
                        rs.getInt("product_id"), rs.getString("title"),
                        rs.getString("category_name"), stock, threshold));
            }
        } catch (SQLException e) {
            System.err.println("[Inventory] load: " + e.getMessage());
        }

        totalProductsLabel.setText(String.valueOf(total));
        inStockLabel.setText(String.valueOf(inStock));
        lowStockLabel.setText(String.valueOf(low));
        outOfStockLabel.setText(String.valueOf(out));
    }

    @FXML
    private void handleThresholdChange() {
        loadInventory();
    }

    /* ── Inline stock edit dialog ─────────────────────────────────── */
    private void showStockEditor(InvRow row) {
        TextInputDialog dlg = new TextInputDialog(String.valueOf(row.getStock()));
        dlg.setTitle("Update Stock");
        dlg.setHeaderText("Product: " + row.getTitle());
        dlg.setContentText("New stock quantity:");
        dlg.showAndWait().ifPresent(input -> {
            try {
                int newQty = Integer.parseInt(input.trim());
                if (newQty < 0) {
                    throw new NumberFormatException();
                }
                updateStock(row.getProductId(), newQty);
                loadInventory();
            } catch (NumberFormatException e) {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Invalid");
                err.setHeaderText(null);
                err.setContentText("Please enter a valid non-negative integer.");
                err.showAndWait();
            }
        });
    }

    private void updateStock(int productId, int newQty) {
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(
                "UPDATE products SET stock_quantity = ? WHERE product_id = ? AND shop_id = ?")) {
            ps.setInt(1, newQty);
            ps.setInt(2, productId);
            ps.setInt(3, shopId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Inventory] updateStock: " + e.getMessage());
        }
    }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML
    private void goShop() {
        App.loadScene("ShopManagement.fxml", "SparkCraft - My Shop");
    }

    @FXML
    private void goProductManagement() {
        App.loadScene("ProductManagement.fxml", "SparkCraft - Manage Products");
    }

    @FXML
    private void goInventory() {
        loadInventory();
    }

    @FXML
    private void goOrderManagement() {
        App.loadScene("OrderManagement.fxml", "SparkCraft - Order Management");
    }

    @FXML
    private void goMessages() {
        App.loadScene("Messaging.fxml", "SparkCraft - Messages");
    }

    @FXML
    private void goDashboard() {
        App.loadScene("Dashboard.fxml", "SparkCraft");
    }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon");
        a.setHeaderText(name);
        a.setContentText("This screen is being built.");
        a.showAndWait();
    }

    /* ── Row model ────────────────────────────────────────────────── */
    public static class InvRow {

        private final SimpleIntegerProperty productId;
        private final SimpleStringProperty title;
        private final SimpleStringProperty categoryName;
        private final SimpleIntegerProperty stock;
        private final SimpleStringProperty statusStr;

        public InvRow(int id, String title, String cat, int stock, int threshold) {
            this.productId = new SimpleIntegerProperty(id);
            this.title = new SimpleStringProperty(title);
            this.categoryName = new SimpleStringProperty(cat != null ? cat : "—");
            this.stock = new SimpleIntegerProperty(stock);
            String st = stock == 0 ? "Out of Stock" : stock <= threshold ? "Low Stock" : "In Stock";
            this.statusStr = new SimpleStringProperty(st);
        }

        public int getProductId() {
            return productId.get();
        }

        public String getTitle() {
            return title.get();
        }

        public String getCategoryName() {
            return categoryName.get();
        }

        public int getStock() {
            return stock.get();
        }

        public String getStatusStr() {
            return statusStr.get();
        }
    }
}
