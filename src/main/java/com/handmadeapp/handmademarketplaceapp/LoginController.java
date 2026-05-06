package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.util.ResourceBundle;


/**
 * cgnfjg
 * @author haryad
 */

public class LoginController {

    @FXML
    private TextField identifierField; // username OR email OR phone
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    

    
    public void initialize(URL url, ResourceBundle rb) {
       
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
            String sql = "SELECT DISTINCT u.*\n" + "FROM users u\n" + "LEFT JOIN addresses a ON u.user_id = a.user_id\n" + "WHERE u.username = ?\n" + "   OR u.email    = ?\n" + "   OR a.phone    = ?\n" + "LIMIT 1\n";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, identifier);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Fix PHP BCrypt $2y$ → $2a$
                String dbHash = rs.getString("password_hash")
                        .replace("$2y$", "$2a$");

                if (BCrypt.checkpw(password, dbHash)) {
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

        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void goToRegister() {
        App.loadScene("Register.fxml", "Register — Handmade Marketplace");
    }
}
