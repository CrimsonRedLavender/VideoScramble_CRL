/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

/**
 * Point d'entree de l'application.
 */
public class MainApp extends Application {
    static {
        // Charge la bibliotheque native OpenCV avant toute manipulation de Mat ou VideoCapture.
        OpenCV.loadLocally();
    }

    /**
     * Charge l'interface et affiche la fenettre de JavaFX.
     *
     * @param stage fenettre de JavaFX
     * @throws Exception si le FXML ne peut pas etre charge
     */
    @Override
    public void start(Stage stage) throws Exception {
        // Le fichier FXML decrit toute l'interface ; le controleur est declare dedans.
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/example/videoscramble/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 780);

        // Configuration minimale de la fenetre principale JavaFX.
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
            // Si des arguments CLI sont fournis, ils sont traites sans ouvrir l'interface graphique.
            int exitCode = CommandLineRunner.run(args);
            if (exitCode >= 0) {
                System.exit(exitCode);
            }
        } catch (Exception e) {
            // Les erreurs CLI sont affichees dans le terminal avec l'aide des commandes disponibles.
            System.err.println("Erreur : " + e.getMessage());
            CommandLineRunner.printUsage();
            System.exit(1);
        }

        // Aucun mode CLI reconnu : on demarre l'application JavaFX classique.
        launch(args);
    }
}
