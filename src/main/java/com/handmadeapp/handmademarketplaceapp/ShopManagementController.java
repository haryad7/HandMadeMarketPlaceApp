package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

/**
 * Controller for ShopManagement.fxml
 * Lets a seller edit their shop name, slug, description, and banner.
 */
public class ShopManagementController implements Initializable {

    @FXML private Label     userNameLabel;
    @FXML private Label     bannerPreviewLabel;
    @FXML private TextField bannerUrlField;
    @FXML private TextField shopNameField;
    @FXML private ComboBox<String> shopStatusCombo;
    @FXML private TextField shopSlugField;
    @FXML private TextArea  shopDescField;
    @FXML private Label     saveFeedbackLabel;

    private int shopId = -1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        User user = SessionManager.getCurrentUser();
        if (user != null) userNameLabel.setText(user.getFullName());

        shopStatusCombo.getItems().addAll("open", "closed");

        loadShop();
    }

    /* ── Load existing shop data ──────────────────────────────────── */
    private void loadShop() {
        User user = SessionManager.getCurrentUser();
        if (user == null) return;

        String sql = "SELECT shop_id, name, url_slug, description, status FROM shops WHERE owner_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, user.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                shopId = rs.getInt("shop_id");
                shopNameField.setText(nullSafe(rs.getString("name")));
                shopSlugField.setText(nullSafe(rs.getString("url_slug")));
                shopDescField.setText(nullSafe(rs.getString("description")));
                String status = rs.getString("status");
                shopStatusCombo.getSelectionModel().select(status != null ? status : "open");
            } else {
                // Seller has no shop yet — will INSERT on save
                shopStatusCombo.getSelectionModel().select("open");
                saveFeedbackLabel.setText("No shop found. Fill in the form to create one.");
            }
        } catch (SQLException e) {
            System.err.println("[ShopMgmt] loadShop: " + e.getMessage());
        }
    }

    /* ── Banner preview ───────────────────────────────────────────── */
    @FXML private void handleBannerPreview() {
        String url = bannerUrlField.getText().trim();
        if (url.isEmpty()) {
            bannerPreviewLabel.setText("No banner set — enter a URL below");
        } else {
            bannerPreviewLabel.setText("Banner URL set: " + url);
        }
    }

    /* ── Save changes ─────────────────────────────────────────────── */
    @FXML private void handleSave() {
        String name   = shopNameField.getText().trim();
        String slug   = shopSlugField.getText().trim();
        String desc   = shopDescField.getText().trim();
        String status = shopStatusCombo.getSelectionModel().getSelectedItem();

        if (name.isEmpty()) {
            showFeedback("Shop name is required.", true);
            return;
        }

        User user = SessionManager.getCurrentUser();
        if (user == null) return;

        try (Connection con = DBConnection.getConnection()) {
            if (shopId < 0) {
                // INSERT new shop
                String ins = "INSERT INTO shops (owner_id, name, url_slug, description, status) VALUES (?,?,?,?,?)";
                PreparedStatement ps = con.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, user.getUserId());
                ps.setString(2, name);
                ps.setString(3, slug.isEmpty() ? null : slug);
                ps.setString(4, desc.isEmpty() ? null : desc);
                ps.setString(5, status);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) shopId = keys.getInt(1);
                showFeedback("Shop created successfully!", false);
            } else {
                // UPDATE existing shop
                String upd = "UPDATE shops SET name=?, url_slug=?, description=?, status=? WHERE shop_id=?";
                PreparedStatement ps = con.prepareStatement(upd);
                ps.setString(1, name);
                ps.setString(2, slug.isEmpty() ? null : slug);
                ps.setString(3, desc.isEmpty() ? null : desc);
                ps.setString(4, status);
                ps.setInt(5, shopId);
                ps.executeUpdate();
                showFeedback("Changes saved!", false);
            }
        } catch (SQLException e) {
            System.err.println("[ShopMgmt] save: " + e.getMessage());
            showFeedback("Save failed: " + e.getMessage(), true);
        }
    }

    private void showFeedback(String msg, boolean isError) {
        saveFeedbackLabel.setText(msg);
        saveFeedbackLabel.setStyle(isError
                ? "-fx-text-fill:#b8372a;-fx-font-size:12px;"
                : "-fx-text-fill:#1a8a5e;-fx-font-size:12px;");
    }

    private String nullSafe(String s) { return s != null ? s : ""; }

    /* ── Navigation ───────────────────────────────────────────────── */
    @FXML private void goShop()              { App.loadScene("ShopManagement.fxml",  "SparkCraft - My Shop"); }
    @FXML private void goProductManagement() { App.loadScene("ProductManagement.fxml", "SparkCraft - Manage Products"); }
    @FXML private void goInventory()         { App.loadScene("Inventory.fxml",         "SparkCraft - Inventory"); }
    @FXML private void goOrderManagement()   { App.loadScene("OrderManagement.fxml", "SparkCraft - Order Management"); }
    @FXML private void goMessages()          { App.loadScene("Messaging.fxml",       "SparkCraft - Messages"); }
    @FXML private void goDashboard()         { App.loadScene("Dashboard.fxml",         "SparkCraft"); }

    private void comingSoon(String name) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Coming Soon"); a.setHeaderText(name);
        a.setContentText("This screen is being built."); a.showAndWait();
    }
}
