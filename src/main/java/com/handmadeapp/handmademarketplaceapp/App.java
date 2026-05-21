package com.handmadeapp.handmademarketplaceapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        primaryStage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            if (!isMaximized) {
                Platform.runLater(() -> primaryStage.setMaximized(true));
            }
        });
        primaryStage.setMaximized(true);
        loadScene("login.fxml", "SparkCraft - Login");
    }

    /**
     * Switches the primary stage to a new FXML scene.
     *
     * The original version only caught IOException, so any other error
     * (NullPointerException when the FXML file isn't found, IllegalStateException
     * from a missing CSS reference, etc.) was silently swallowed and the screen
     * simply never changed.  This version:
     *   1. Checks that the FXML file actually exists before trying to load it.
     *   2. Catches ALL exceptions so nothing is silently hidden.
     *   3. Prints a clear error to the NetBeans Output window so you can see
     *      exactly what went wrong.
     */
    public static void loadScene(String fxmlFile, String title) {
        try {
            // Step 1 — locate the resource
            URL resource = App.class.getResource(fxmlFile);
            if (resource == null) {
                System.err.println("[loadScene] FXML file not found: " + fxmlFile);
                System.err.println("  Check the file exists in: src/main/resources/com/handmadeapp/handmademarketplaceapp/");
                System.err.println("  Then do Clean and Build (Shift+F11) so NetBeans copies it to the output folder.");
                return;
            }

            // Step 2 — load and display
            boolean wasFullScreen = primaryStage.isFullScreen();

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            tuneScrollPanes(root);
            Scene scene = new Scene(root);
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.show();
            primaryStage.setMaximized(true);
            if (wasFullScreen) {
                Platform.runLater(() -> primaryStage.setFullScreen(true));
            } else {
                Platform.runLater(() -> primaryStage.setMaximized(true));
            }

        } catch (Exception e) {
            // Catches IOException, NullPointerException, IllegalStateException,
            // and anything else — nothing is silently hidden any more.
            System.err.println("[loadScene] Failed to load: " + fxmlFile);
            System.err.println("  Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void tuneScrollPanes(Node node) {
        if (node instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) node;
            scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (event.getDeltaY() == 0 || scrollPane.getContent() == null) return;
                double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();
                double scrollableHeight = Math.max(1, contentHeight - viewportHeight);
                scrollPane.setVvalue(scrollPane.getVvalue() - (event.getDeltaY() * 2.5 / scrollableHeight));
                event.consume();
            });
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                tuneScrollPanes(child);
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
