package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.util.ResourceBundle;

/**
 *
 * @author haryad
 */
public class LoginController {

    @FXML
    private TextField identifierField; // username OR email OR phone
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

//    
    public void initialize(URL url, ResourceBundle rb) {
        ensureDefaultAdminAccount();
    }

    @FXML
    private void handleLogin() {
        String identifier = identifierField.getText().trim();
        String password = passwordField.getText();

        if (identifier.isEmpty() || password.isEmpty()) {
            errorLabel.setText("⚠ Please fill in all fields.");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {

            // Search by username, email, OR phone (from addresses table)
            String sql = "SELECT DISTINCT u.* "
                    + "FROM users u "
                    + "LEFT JOIN addresses a ON u.user_id = a.user_id "
                    + "WHERE u.username = ? "
                    + "OR u.email = ? "
                    + "OR a.phone = ? "
                    + "LIMIT 1";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, identifier);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Fix PHP BCrypt $2y$ → $2a$
                String dbHash = normalizeBCryptHash(rs.getString("password_hash"));

                if (dbHash != null && BCrypt.checkpw(password, dbHash)) {
                    User user = new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("role")
                    );
                    SessionManager.setCurrentUser(user);
                    App.loadScene("Dashboard.fxml",
                            "Dashboard — " + user.getFullName());
                } else {
                    errorLabel.setText(" Incorrect password.");
                }
            } else {
                errorLabel.setText(" No account found with that username, email, or phone.");
            }

        } catch (IllegalArgumentException e) {
            errorLabel.setText("Invalid saved password hash for this account.");
            System.err.println("[Login] Invalid password hash: " + e.getMessage());
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void goToRegister() {
        App.loadScene("Register.fxml", "Register — Handmade Marketplace");
    }
    private void ensureDefaultAdminAccount() {
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT user_id FROM users WHERE username = 'admin' LIMIT 1");
                 ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    resetAdminPassword(conn, rs.getInt("user_id"));
                    return;
                }
            }

            String passwordHash = BCrypt.hashpw("admin123", BCrypt.gensalt(12));
            String sql = "INSERT INTO users "
                    + "(first_name, last_name, username, email, password_hash, role, date_of_birth) "
                    + "VALUES (?, ?, ?, ?, ?, 'admin', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "System");
                ps.setString(2, "Admin");
                ps.setString(3, "admin");
                ps.setString(4, "admin@sparkcraft.local");
                ps.setString(5, passwordHash);
                ps.setDate(6, Date.valueOf("1990-01-01"));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Login] Could not create default admin account: " + e.getMessage());
        }
    }

    private void resetAdminPassword(Connection conn, int userId) throws SQLException {
        String passwordHash = BCrypt.hashpw("admin123", BCrypt.gensalt(12));
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET first_name = 'System', last_name = 'Admin', "
                        + "email = 'admin@sparkcraft.local', role = 'admin', password_hash = ? "
                        + "WHERE user_id = ?")) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private String normalizeBCryptHash(String hash) {
        if (hash == null) {
            return null;
        }
        String normalized = hash.trim()
                .replace("$2y$", "$2a$")
                .replace("$2b$", "$2a$");
        return normalized.matches("^\\$2a\\$\\d\\d\\$[./A-Za-z0-9]{53}$") ? normalized : null;
    }
}
