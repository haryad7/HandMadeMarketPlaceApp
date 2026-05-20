package com.handmadeapp.handmademarketplaceapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
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
            FXMLLoader loader = new FXMLLoader(resource);
            Scene scene = new Scene(loader.load());
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            // Catches IOException, NullPointerException, IllegalStateException,
            // and anything else — nothing is silently hidden any more.
            System.err.println("[loadScene] Failed to load: " + fxmlFile);
            System.err.println("  Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}