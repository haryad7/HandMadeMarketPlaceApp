package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;

/**
 * Step 1 of checkout: use the registered address or collect a different
 * shipping address, then move to Payment.
 */
public class OrderController {

    @FXML private TextField recipientField, phoneField, line1Field, line2Field, postalField;
    @FXML private ComboBox<String> cityCombo;
    @FXML private RadioButton useRegisteredInfo, useDifferentInfo;
    @FXML private Label errorLabel, itemsLine, subtotalLine, shippingLine, totalLine;

    private int registeredAddressId = 0;

    @FXML
    public void initialize() {
        loadCities();
        loadSummary();
        loadRegisteredInfo();
        handleAddressChoice();
    }

    private void loadSummary() {
        double subtotal = CheckoutContext.getCartTotal();
        double shipping = subtotal > 0 ? 5.00 : 0.00;
        double total = subtotal + shipping;
        itemsLine.setText("Cart total");
        subtotalLine.setText(String.format("Subtotal: $%.2f", subtotal));
        shippingLine.setText(String.format("Shipping: $%.2f", shipping));
        totalLine.setText(String.format("$%.2f", total));
    }

    private void loadRegisteredInfo() {
        User user = SessionManager.getCurrentUser();
        if (user == null) return;

        recipientField.setText(user.getFullName());
        String sql = "SELECT address_id, phone, address_line1, address_line2, city, postal_code "
                + "FROM addresses WHERE user_id = ? "
                + "ORDER BY CASE WHEN address_type IS NULL OR address_type = '' THEN 0 ELSE 1 END, "
                + "is_default DESC, address_id ASC LIMIT 1";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, user.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                registeredAddressId = rs.getInt("address_id");
                String phone = rs.getString("phone");
                if (phone == null || phone.isBlank()) phone = findRegisteredPhone(con, user.getUserId());
                phoneField.setText(nullSafe(phone));
                line1Field.setText(nullSafe(rs.getString("address_line1")));
                line2Field.setText(nullSafe(rs.getString("address_line2")));
                postalField.setText(nullSafe(rs.getString("postal_code")));
                selectCity(rs.getString("city"));
            } else {
                useDifferentInfo.setSelected(true);
                errorLabel.setText("No registered address found. Please enter shipping info.");
            }
        } catch (SQLException e) {
            useDifferentInfo.setSelected(true);
            errorLabel.setText("Could not load registered info: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddressChoice() {
        boolean editing = useDifferentInfo.isSelected() || registeredAddressId == 0;
        recipientField.setDisable(!editing);
        phoneField.setDisable(!editing);
        line1Field.setDisable(!editing);
        line2Field.setDisable(!editing);
        cityCombo.setDisable(!editing);
        postalField.setDisable(!editing);
    }

    @FXML
    private void handleContinue() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            errorLabel.setText("You must be logged in to place an order.");
            return;
        }

        if (useRegisteredInfo.isSelected() && registeredAddressId > 0) {
            CheckoutContext.setShippingAddressId(registeredAddressId);
            App.loadScene("Payment.fxml", "Checkout - Payment");
            return;
        }

        if (!validate()) return;

        String sql = "INSERT INTO addresses (user_id, recipient_name, phone, "
                + "address_line1, address_line2, city, postal_code, address_type, is_default) "
                + "VALUES (?,?,?,?,?,?,?,?,0)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, user.getUserId());
            ps.setString(2, recipientField.getText().trim());
            ps.setString(3, phoneField.getText().trim());
            ps.setString(4, line1Field.getText().trim());
            ps.setString(5, line2Field.getText().trim().isEmpty() ? null : line2Field.getText().trim());
            ps.setString(6, cityCombo.getValue());
            ps.setString(7, postalField.getText().trim().isEmpty() ? null : postalField.getText().trim());
            ps.setString(8, "shipping");
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) CheckoutContext.setShippingAddressId(keys.getInt(1));
            App.loadScene("Payment.fxml", "Checkout - Payment");
        } catch (SQLException e) {
            errorLabel.setText("Could not save address: " + e.getMessage());
        }
    }

    @FXML
    private void goBack() {
        App.loadScene("Cart.fxml", "SparkCraft - My Cart");
    }

    private boolean validate() {
        if (recipientField.getText().trim().isEmpty()
                || phoneField.getText().trim().isEmpty()
                || line1Field.getText().trim().isEmpty()
                || cityCombo.getValue() == null
                || cityCombo.getValue().isBlank()) {
            errorLabel.setText("Please fill in recipient, phone, address, and city.");
            return false;
        }
        errorLabel.setText("");
        return true;
    }

    private void loadCities() {
        ObservableList<String> cities = FXCollections.observableArrayList();
        try (InputStream is = getClass().getResourceAsStream("iraq_cities.txt")) {
            if (is == null) throw new IllegalStateException("iraq_cities.txt not found");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) cities.add(formatCity(line));
                }
            }
        } catch (Exception e) {
            errorLabel.setText("Could not load city list: " + e.getMessage());
        }
        cityCombo.setItems(cities);
    }

    private void selectCity(String city) {
        if (city == null || city.isBlank()) return;
        String formatted = formatCity(city);
        if (!cityCombo.getItems().contains(formatted)) cityCombo.getItems().add(formatted);
        cityCombo.getSelectionModel().select(formatted);
    }

    private String formatCity(String raw) {
        return java.util.Arrays.stream(raw.trim().split("\\s+"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(raw.trim());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String findRegisteredPhone(Connection con, int userId) throws SQLException {
        String sql = "SELECT phone FROM addresses "
                + "WHERE user_id = ? AND phone IS NOT NULL AND phone <> '' "
                + "ORDER BY CASE WHEN address_type IS NULL OR address_type = '' THEN 0 ELSE 1 END, "
                + "address_id ASC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("phone") : "";
        }
    }
}
