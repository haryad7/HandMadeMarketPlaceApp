package com.handmadeapp.handmademarketplaceapp;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    /* ─── Sidebar labels ──────────────────────────────────────────── */
    @FXML
    private Label userNameLabel;
    @FXML
    private Label roleBadgeLabel;

    /* ─── Sidebar nav sections (shown/hidden per role) ────────────── */
    @FXML
    private VBox buyerSection;
    @FXML
    private VBox sellerSection;
    @FXML
    private VBox adminSection;

    /* ─── Main content ────────────────────────────────────────────── */
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label dateLabel;

    /* ─── Main content card grids (shown/hidden per role) ─────────── */
    @FXML
    private VBox buyerCards;
    @FXML
    private VBox sellerCards;
    @FXML
    private VBox adminCards;

    /* ─── Init ────────────────────────────────────────────────────── */
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Guard: if somehow we got here without logging in, go back
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            App.loadScene("login.fxml", "SparkCraft - Login");
            return;
        }

        // ── Sidebar user info ────────────────────────────────────────
        userNameLabel.setText(user.getFullName());
        String role = user.getRole().toLowerCase();
        roleBadgeLabel.setText(role.toUpperCase());
        roleBadgeLabel.getStyleClass().add("role-badge-" + role); // badge colour per role

        // ── Main area welcome ────────────────────────────────────────
        welcomeLabel.setText("Welcome back, " + user.getFirstName() + "!");
        dateLabel.setText(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy")));

        // ── Show only the sections that match the logged-in role ─────
        show(buyerSection, role.equals("buyer"));
        show(sellerSection, role.equals("seller"));
        show(adminSection, role.equals("admin"));

        show(buyerCards, role.equals("buyer"));
        show(sellerCards, role.equals("seller"));
        show(adminCards, role.equals("admin"));
    }

    /* ─── Buyer navigation ────────────────────────────────────────── */
    @FXML
    private void goBrowseProducts() {
        navigate("ProductListing.fxml", "SparkCraft - Browse");
    }

    @FXML
    private void goCart() {
        navigate("Cart.fxml", "SparkCraft - My Cart");
    }

    @FXML
    private void goOrders() {
        comingSoon("My Orders");
    }

    /* ─── Seller navigation ───────────────────────────────────────── */
    @FXML
    private void goShop() {
        navigate("ShopManagement.fxml", "SparkCraft - My Shop");
    }

    @FXML
    private void goProductManagement() {
        navigate("ProductManagement.fxml", "SparkCraft - Manage Products");
    }

    @FXML
    private void goInventory() {
        navigate("Inventory.fxml", "SparkCraft - Inventory");
    }

    @FXML
    private void goOrderManagement() {
        comingSoon("Order Management");
    }

    /* ─── Admin navigation ────────────────────────────────────────── */
    @FXML
    private void goManageUsers() {
        comingSoon("Manage Users");
    }

    @FXML
    private void goManageProductsAdmin() {
        comingSoon("Manage Products (Admin)");
    }

    @FXML
    private void goMonitorOrders() {
        comingSoon("Monitor Orders");
    }

    @FXML
    private void goReports() {
        comingSoon("View Reports");
    }

    /* ─── Shared ──────────────────────────────────────────────────── */
    @FXML
    private void goMessages() {
        comingSoon("Messages");
    }

    /* ─── Logout ──────────────────────────────────────────────────── */
    @FXML
    private void handleLogout() {
        SessionManager.logout();
        App.loadScene("login.fxml", "SparkCraft - Login");
    }

    /* ─── Helpers ─────────────────────────────────────────────────── */
    /**
     * Navigates to an FXML screen. If the file does not exist yet, falls back
     * to the comingSoon dialog so the UI never silently breaks.
     */
    private void navigate(String fxml, String title) {
        try {
            if (App.class.getResource(fxml) == null) {
                comingSoon(title);
                return;
            }
            App.loadScene(fxml, title);
        } catch (Exception e) {
            comingSoon(title);
        }
    }

    /**
     * Shows a friendly "in development" dialog. Replace with navigate() calls
     * as each screen is built.
     */
    private void comingSoon(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Coming Soon");
        alert.setHeaderText(feature);
        alert.setContentText("This screen is currently being built. Check back soon!");
        alert.showAndWait();
    }

    /**
     * Hides or shows a node AND removes it from the layout when hidden, so
     * invisible sections do not leave blank space.
     */
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
