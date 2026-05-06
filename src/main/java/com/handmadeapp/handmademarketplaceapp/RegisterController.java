package com.handmadeapp.handmademarketplaceapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    /* ─── FXML Fields ─────────────────────────────────────────────── */
    @FXML private TextField        firstNameField;
    @FXML private TextField        lastNameField;
    @FXML private TextField        usernameField;
    @FXML private TextField        emailField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private PasswordField    passwordField;
    @FXML private PasswordField    confirmPasswordField;
    @FXML private Label            errorLabel;
    @FXML private Label            successLabel;
    @FXML private Button           registerButton;

    /* ─── Init ────────────────────────────────────────────────────── */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        roleCombo.setItems(FXCollections.observableArrayList("Buyer", "Seller"));
        roleCombo.getSelectionModel().selectFirst();
    }

    /* ─── Register Handler ────────────────────────────────────────── */
    @FXML
    private void handleRegister() {
        clearMessages();

        String firstName       = firstNameField.getText().trim();
        String lastName        = lastNameField.getText().trim();
        String username        = usernameField.getText().trim();
        String email           = emailField.getText().trim();
        String role            = roleCombo.getValue();
        String password        = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // ── Validation ──────────────────────────────────────────────
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty()
                || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        if (firstName.length() < 2) {
            showError("First name must be at least 2 characters.");
            return;
        }

        if (lastName.length() < 2) {
            showError("Last name must be at least 2 characters.");
            return;
        }

        if (username.length() < 3 || !username.matches("[a-zA-Z0-9_]+")) {
            showError("Username must be 3+ characters: letters, numbers, or underscores only.");
            return;
        }

        if (!isValidEmail(email)) {
            showError("Please enter a valid email address.");
            return;
        }

        if (role == null) {
            showError("Please select an account type.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        // ── Create User ─────────────────────────────────────────────
        // Constructor: User(int userId, String username, String email,
        //                   String firstName, String lastName, String role)
        int newUserId = generateUserId();
        User newUser = new User(newUserId, username, email, firstName, lastName, role);

        // TODO: Save newUser to your database here
        // e.g. UserDAO.save(newUser);

        showSuccess("Account created successfully! Redirecting to login...");
        registerButton.setDisable(true);

        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
        pause.setOnFinished(e -> goToLogin());
        pause.play();
    }

    /* ─── Navigate to Login ───────────────────────────────────────── */
    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/handmadeapp/handmademarketplaceapp/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("SparkCraft - Login");
        } catch (IOException e) {
            e.printStackTrace();
            showError("Could not navigate to login screen.");
        }
    }

    /* ─── Helpers ─────────────────────────────────────────────────── */
    private boolean isValidEmail(String email) {
        return email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");
    }

    private int generateUserId() {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        successLabel.setText("");
    }

    private void showSuccess(String message) {
        successLabel.setText(message);
        errorLabel.setText("");
    }

    private void clearMessages() {
        errorLabel.setText("");
        successLabel.setText("");
    }
}