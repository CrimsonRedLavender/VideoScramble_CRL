/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier lance l'application JavaFX.
 */

package com.example.videoscramble;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

/**
 * Point d'entree graphique de l'application VideoScramble.
 */
public class MainApp extends Application {
    static {
        OpenCV.loadLocally();
    }

    /**
     * Charge l'interface principale et affiche la fenetre JavaFX.
     *
     * @param stage fenetre principale fournie par JavaFX
     * @throws Exception si le fichier FXML ne peut pas etre charge
     */
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/example/videoscramble/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 780);
        stage.setTitle("VideoScramble - JavaFX + OpenCV");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Lance l'application JavaFX.
     *
     * @param args arguments transmis par la ligne de commande
     */
    public static void main(String[] args) {
        try {
            int exitCode = CommandLineRunner.run(args);
            if (exitCode >= 0) {
                System.exit(exitCode);
            }
        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            CommandLineRunner.printUsage();
            System.exit(1);
        }
        launch(args);
    }
}
