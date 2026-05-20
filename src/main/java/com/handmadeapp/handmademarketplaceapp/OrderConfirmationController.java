package com.handmadeapp.handmademarketplaceapp;

import com.handmadeapp.handmademarketplaceapp.DB.DBConnection;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.*;

/**
 * Step 3 of checkout: show success info for the order created in
 * PaymentController, and let the buyer submit a 1-5 star product review.
 */
public class OrderConfirmationController {

    @FXML private Label  orderNumberLabel, totalPaidLabel, reviewMsg;
    @FXML private Button star1, star2, star3, star4, star5;
    @FXML private TextArea commentArea;

    private int rating = 0;

    @FXML
    public void initialize() {
        int orderId = CheckoutContext.getOrderId();
        double total = CheckoutContext.getCartTotal() + 5.00;

        if (orderId > 0) orderNumberLabel.setText("Order #" + orderId);
        totalPaidLabel.setText(String.format("Total Paid: $%.2f", total));
        applyStars();
    }

    @FXML private void rate1() { rating = 1; applyStars(); }
    @FXML private void rate2() { rating = 2; applyStars(); }
    @FXML private void rate3() { rating = 3; applyStars(); }
    @FXML private void rate4() { rating = 4; applyStars(); }
    @FXML private void rate5() { rating = 5; applyStars(); }

    private void applyStars() {
        Button[] stars = { star1, star2, star3, star4, star5 };
        for (int i = 0; i < 5; i++) {
            stars[i].getStyleClass().remove("star-on");
            if (i < rating) stars[i].getStyleClass().add("star-on");
        }
    }

    @FXML
    private void handleSubmit() {
        User user    = SessionManager.getCurrentUser();
        int orderId  = CheckoutContext.getOrderId();
        if (user == null || orderId == 0) { reviewMsg.setText("No order to review."); return; }
        if (rating == 0)                  { reviewMsg.setText("Please choose 1–5 stars."); return; }

        // Find one product from the order to attach the review to.
        String findSql = "SELECT product_id FROM orderitems WHERE order_id = ? LIMIT 1";
        String insertSql = "INSERT INTO productreviews (product_id, buyer_id, rating, comment) VALUES (?,?,?,?)";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement find = c.prepareStatement(findSql)) {

            find.setInt(1, orderId);
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) { reviewMsg.setText("No items found for this order."); return; }
                int productId = rs.getInt(1);

                try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                    ins.setInt   (1, productId);
                    ins.setInt   (2, user.getUserId());
                    ins.setInt   (3, rating);
                    ins.setString(4, commentArea.getText().trim());
                    ins.executeUpdate();
                }
            }
            reviewMsg.setText("Thanks — your review was saved!");
            commentArea.setDisable(true);
            setReviewControlsDisabled(true);

        } catch (SQLException ex) {
            reviewMsg.setText("Could not save review: " + ex.getMessage());
        }
    }

    @FXML
    private void handleSkipReview() {
        reviewMsg.setText("Review skipped. Your order is already confirmed.");
        commentArea.clear();
        setReviewControlsDisabled(true);
    }

    private void setReviewControlsDisabled(boolean disabled) {
        Button[] stars = { star1, star2, star3, star4, star5 };
        for (Button star : stars) star.setDisable(disabled);
        commentArea.setDisable(disabled);
    }

    @FXML private void goDashboard() { CheckoutContext.reset(); App.loadScene("Dashboard.fxml",      "Dashboard"); }
    @FXML private void goMyOrders()  {                          App.loadScene("MyOrders.fxml",       "My Orders"); }
}
