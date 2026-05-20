package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.*;

/**
 * Shared in-app messaging: browse conversations on the left, read & send
 * messages on the right. Works for buyers, sellers and admins because it
 * just lists every conversation the current user belongs to.
 */
public class MessagingController {

    public static class Conv {
        public final int    conversationId;
        public final String label;
        public Conv(int id, String label) { this.conversationId = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    @FXML private ListView<Conv> convList;
    @FXML private VBox           msgList;
    @FXML private ScrollPane     msgScroll;
    @FXML private TextField      messageField;
    @FXML private Label          chatHeader;

    private Conv activeConv;
    private static int targetConversationId = 0;

    public static void setTargetConversationId(int conversationId) {
        targetConversationId = conversationId;
    }

    public static int findOrCreateConversation(int buyerId, int sellerId) throws SQLException {
        ensureMessagingTables();
        try (Connection con = DBConnection.getConnection()) {
            String findSql = "SELECT conversation_id FROM conversations "
                    + "WHERE buyer_id = ? AND seller_id = ? LIMIT 1";
            try (PreparedStatement find = con.prepareStatement(findSql)) {
                find.setInt(1, buyerId);
                find.setInt(2, sellerId);
                ResultSet rs = find.executeQuery();
                if (rs.next()) return rs.getInt("conversation_id");
            }

            String insertSql = "INSERT INTO conversations (buyer_id, seller_id) VALUES (?, ?)";
            try (PreparedStatement insert = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, buyerId);
                insert.setInt(2, sellerId);
                insert.executeUpdate();
                ResultSet keys = insert.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    @FXML
    public void initialize() {
        convList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            activeConv = newV;
            if (newV != null) {
                chatHeader.setText(newV.label);
                loadMessages();
            }
        });
        loadConversations();
    }

    @FXML
    public void loadConversations() {
        User u = SessionManager.getCurrentUser();
        if (u == null) return;
        ensureMessagingTables();

        ObservableList<Conv> rows = FXCollections.observableArrayList();
        String sql =
            "SELECT c.conversation_id, " +
            "       CONCAT(IFNULL(ub.first_name,'?'),' ',IFNULL(ub.last_name,''), " +
            "              '  ↔  ', " +
            "              IFNULL(us.first_name,'?'),' ',IFNULL(us.last_name,'')) AS label " +
            "FROM conversations c " +
            "LEFT JOIN users ub ON c.buyer_id  = ub.user_id " +
            "LEFT JOIN users us ON c.seller_id = us.user_id " +
            "WHERE c.buyer_id = ? OR c.seller_id = ? " +
            "ORDER BY c.conversation_id DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, u.getUserId());
            ps.setInt(2, u.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Conv(rs.getInt("conversation_id"), rs.getString("label")));
                }
            }
        } catch (SQLException ex) {
            // Admins may not be a buyer or seller in any conversation: show all
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT conversation_id FROM conversations ORDER BY conversation_id DESC")) {
                while (rs.next()) rows.add(new Conv(rs.getInt(1), "Conversation #" + rs.getInt(1)));
            } catch (SQLException ignore) { /* ignore */ }
        }
        convList.setItems(rows);
        if (!rows.isEmpty()) {
            int selectIndex = 0;
            if (targetConversationId > 0) {
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).conversationId == targetConversationId) {
                        selectIndex = i;
                        break;
                    }
                }
            }
            convList.getSelectionModel().select(selectIndex);
            targetConversationId = 0;
        }
    }

    public void loadMessages() {
        msgList.getChildren().clear();
        if (activeConv == null) return;
        User u = SessionManager.getCurrentUser();
        if (u == null) return;

        String sql = "SELECT sender_id, body, sent_at FROM messages " +
                     "WHERE conversation_id = ? ORDER BY sent_at ASC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, activeConv.conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean me = rs.getInt("sender_id") == u.getUserId();
                    msgList.getChildren().add(buildBubble(
                            rs.getString("body"),
                            String.valueOf(rs.getTimestamp("sent_at")),
                            me));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        Platform.runLater(() -> msgScroll.setVvalue(1.0));
    }

    private HBox buildBubble(String body, String when, boolean me) {
        Label bubble = new Label(body);
        bubble.getStyleClass().add(me ? "msg-bubble-me" : "msg-bubble-them");
        bubble.setWrapText(true);

        Label meta = new Label(when);
        meta.getStyleClass().add("msg-meta");

        VBox v = new VBox(3, bubble, meta);
        v.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(v);
        row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    @FXML
    private void handleSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || activeConv == null) return;
        User u = SessionManager.getCurrentUser();
        if (u == null) return;
        ensureMessagingTables();

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO messages (conversation_id, sender_id, body) VALUES (?,?,?)")) {
            ps.setInt   (1, activeConv.conversationId);
            ps.setInt   (2, u.getUserId());
            ps.setString(3, text);
            ps.executeUpdate();
            messageField.clear();
            loadMessages();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @FXML private void goBack() { App.loadScene("Dashboard.fxml", "Dashboard"); }

    static void ensureMessagingTables() {
        String conversationsSql = "CREATE TABLE IF NOT EXISTS conversations ("
                + "conversation_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "buyer_id INT NOT NULL, "
                + "seller_id INT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uq_buyer_seller (buyer_id, seller_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String messagesSql = "CREATE TABLE IF NOT EXISTS messages ("
                + "message_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "conversation_id INT NOT NULL, "
                + "sender_id INT NOT NULL, "
                + "body TEXT NOT NULL, "
                + "sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            st.execute(conversationsSql);
            st.execute(messagesSql);
        } catch (SQLException e) {
            System.err.println("[Messaging] ensureTables: " + e.getMessage());
        }
    }
}
