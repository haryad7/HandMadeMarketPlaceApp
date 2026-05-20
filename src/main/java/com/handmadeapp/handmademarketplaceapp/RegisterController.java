package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    /* ─── FXML Fields ─────────────────────────────────────────────── */
    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressLineField;
    @FXML
    private DatePicker dobPicker;
    @FXML
    private ComboBox<String> roleCombo;
    @FXML
    private ComboBox<String> cityCombo;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;
    @FXML
    private Button registerButton;

    /* ─── Init ────────────────────────────────────────────────────── */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        roleCombo.setItems(FXCollections.observableArrayList("Buyer", "Seller"));
        roleCombo.getSelectionModel().selectFirst();
        loadCitiesAsync();

        // Restrict DatePicker so users cannot pick a future date
        dobPicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
    }

    /* ─── Register Handler ────────────────────────────────────────── */
    @FXML
    private void handleRegister() {
        clearMessages();

        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String address = addressLineField.getText().trim();
        LocalDate dob = dobPicker.getValue();
        String role = roleCombo.getValue();
        String city = cityCombo.getValue();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // ── Validation ──────────────────────────────────────────────
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty()
                || email.isEmpty() || phone.isEmpty() || password.isEmpty()
                || confirmPassword.isEmpty()) {
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

        if (!isValidPhone(phone)) {
            showError("Please enter a valid phone number (7–15 digits).");
            return;
        }

        if (dob == null) {
            showError("Please select your date of birth.");
            return;
        }
        if (dob.isAfter(LocalDate.now().minusYears(13))) {
            showError("You must be at least 13 years old to register.");
            return;
        }

        if (role == null) {
            showError("Please select an account type.");
            return;
        }

        if (city == null || city.isBlank()) {
            showError("Please select your city.");
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
        if (address == null) {
            showError("address required!");
            return;
        }

        // ── Save to Database ────────────────────────────────────────
        try (Connection conn = DBConnection.getConnection()) {

            // 1. Check for duplicate username
            if (isDuplicate(conn, "SELECT 1 FROM users WHERE username = ?", username)) {
                showError("That username is already taken. Please choose another.");
                return;
            }

            // 2. Check for duplicate email
            if (isDuplicate(conn, "SELECT 1 FROM users WHERE email = ?", email)) {
                showError("An account with that email already exists.");
                return;
            }

            // 3. Hash the password with BCrypt (cost factor 12)
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

            // 4. INSERT into users — let MySQL AUTO_INCREMENT assign user_id
            String insertUser
                    = "INSERT INTO users (first_name, last_name, username, email, password_hash, role, date_of_birth) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            int newUserId;
            try (PreparedStatement ps = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, firstName);
                ps.setString(2, lastName);
                ps.setString(3, username);
                ps.setString(4, email);
                ps.setString(5, passwordHash);
                ps.setString(6, role.toLowerCase());   // store as "buyer" / "seller"
                ps.setDate(7, Date.valueOf(dob));       // java.time.LocalDate → java.sql.Date
                ps.executeUpdate();

                // Retrieve the auto-generated user_id
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        newUserId = keys.getInt(1);
                    } else {
                        showError("Registration failed: could not retrieve new user ID.");
                        return;
                    }
                }
            }

            // 5. INSERT into addresses (phone lives here, matching LoginController's JOIN)
            String insertAddress
                    = "INSERT INTO addresses (user_id, phone, address_line1, city) VALUES (?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(insertAddress)) {
                ps.setInt(1, newUserId);
                ps.setString(2, phone);
                ps.setString(3, address);
                ps.setString(4, city);
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
            return;
        }

        // ── Success: show message then navigate to Login ────────────
        showSuccess("Account created successfully! Redirecting to login...");
        registerButton.setDisable(true);

        javafx.animation.PauseTransition pause
                = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
        pause.setOnFinished(e -> goToLogin());
        pause.play();
    }

    /* ─── Load Cities Async ───────────────────────────────────────── */
    private void loadCitiesAsync() {
        Task<ObservableList<String>> loadTask = new Task<>() {
            @Override
            protected ObservableList<String> call() throws Exception {
                ObservableList<String> cities = FXCollections.observableArrayList();

                InputStream is = getClass().getResourceAsStream("iraq_cities.txt");
                if (is == null) {
                    throw new Exception("iraq_cities.txt not found in resources!");
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            // Capitalize first letter of each word
                            String formatted = java.util.Arrays.stream(line.trim().split("\\s+"))
                                    .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                                    .reduce((a, b) -> a + " " + b)
                                    .orElse(line.trim());
                            cities.add(formatted);
                        }
                    }
                }
                return cities;
            }
        };

        loadTask.setOnSucceeded(e -> {
            cityCombo.setItems(loadTask.getValue());
            cityCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select city" : item);
                }
            });
        });

        loadTask.setOnFailed(e -> {
            Throwable error = loadTask.getException();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Load Error");
            alert.setHeaderText("Could not load city list");
            alert.setContentText(error.getMessage());
            alert.show();
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }

    /* ─── Navigate to Login ───────────────────────────────────────── */
    @FXML
    private void goToLogin() {
        // Use App.loadScene() — same pattern as LoginController.goToRegister()
        App.loadScene("login.fxml", "SparkCraft - Login");
    }

    /* ─── Helpers ─────────────────────────────────────────────────── */
    /**
     * Returns true if the given query finds at least one row. Used for
     * duplicate-check queries like "SELECT 1 FROM users WHERE username = ?".
     */
    private boolean isDuplicate(Connection conn, String sql, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * Accepts formats like: +964 750 123 4567, 07501234567, +1-800-555-0199.
     * Strips spaces and dashes, then checks for 7–15 digits with an optional
     * leading +.
     */
    private boolean isValidPhone(String phone) {
        String normalized = phone.replaceAll("[\\s\\-]", "");
        return normalized.matches("^\\+?[0-9]{7,15}$");
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
