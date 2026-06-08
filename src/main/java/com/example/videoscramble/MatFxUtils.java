/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayInputStream;

/**
 * Fonctions utilitaire pour afficher des matrices OpenCV dans JavaFX.
 */
public final class MatFxUtils {
    private MatFxUtils() {
    }

    /**
     * Convertit une matrice en image.
     *
     * @param mat matrice OpenCV
     * @return image JavaFX
     */
    public static Image matToImage(Mat mat) {
        // Une Mat vide ne peut pas etre affichee dans un ImageView JavaFX.
        if (mat == null || mat.empty()) {
            return null;
        }

        // JavaFX ne sait pas lire directement une Mat OpenCV : on l'encode en PNG en memoire.
        MatOfByte buffer = new MatOfByte();
        boolean ok = Imgcodecs.imencode(".png", mat, buffer);

        // Si l'encodage echoue, on evite de construire une Image invalide.
        if (!ok || buffer.empty()) {
            return null;
        }

        // Le buffer PNG est transforme en flux lisible par le constructeur Image de JavaFX.
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
}
