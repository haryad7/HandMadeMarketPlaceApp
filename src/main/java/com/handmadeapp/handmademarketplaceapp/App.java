package com.handmadeapp.handmademarketplaceapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    private static Stage primaryStage;
    private static double dragOffsetX;
    private static double dragOffsetY;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
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
            StageState stageState = StageState.capture(primaryStage);

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            tuneScrollPanes(root);
            installWindowDragHandlers(root);

            Scene scene = stageState.hasScene
                    ? new Scene(root, stageState.width, stageState.height)
                    : new Scene(root);

            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.runLater(() -> stageState.restore(primaryStage));

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

    private static void installWindowDragHandlers(Node node) {
        if (node.getStyleClass().contains("topbar")) {
            node.setOnMousePressed(event -> {
                if (event.getButton() != MouseButton.PRIMARY || isWindowDragBlocked(event.getTarget())) {
                    return;
                }
                dragOffsetX = event.getScreenX() - primaryStage.getX();
                dragOffsetY = event.getScreenY() - primaryStage.getY();
            });
            node.setOnMouseDragged(event -> {
                if (event.getButton() != MouseButton.PRIMARY || isWindowDragBlocked(event.getTarget())) {
                    return;
                }
                primaryStage.setX(event.getScreenX() - dragOffsetX);
                primaryStage.setY(event.getScreenY() - dragOffsetY);
            });
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                installWindowDragHandlers(child);
            }
        }
    }

    private static boolean isWindowDragBlocked(EventTarget target) {
        if (primaryStage.isMaximized() || primaryStage.isFullScreen()) {
            return true;
        }
        if (!(target instanceof Node)) {
            return false;
        }

        Node node = (Node) target;
        while (node != null && !node.getStyleClass().contains("topbar")) {
            if (node instanceof ButtonBase || node instanceof TextInputControl) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static final class StageState {
        private final boolean hasScene;
        private final boolean maximized;
        private final boolean fullScreen;
        private final boolean iconified;
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        private StageState(Stage stage) {
            hasScene = stage.getScene() != null;
            maximized = stage.isMaximized();
            fullScreen = stage.isFullScreen();
            iconified = stage.isIconified();
            x = stage.getX();
            y = stage.getY();
            width = Math.max(1, stage.getWidth());
            height = Math.max(1, stage.getHeight());
        }

        private static StageState capture(Stage stage) {
            return new StageState(stage);
        }

        private void restore(Stage stage) {
            if (!maximized && !fullScreen) {
                stage.setX(x);
                stage.setY(y);
                stage.setWidth(width);
                stage.setHeight(height);
            }
            stage.setMaximized(maximized);
            stage.setFullScreen(fullScreen);
            stage.setIconified(iconified);
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
