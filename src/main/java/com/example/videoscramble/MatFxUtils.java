/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier convertit les images OpenCV pour l'affichage JavaFX.
 */

package com.example.videoscramble;

import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayInputStream;

/**
 * Fonctions utilitaires pour afficher des matrices OpenCV dans JavaFX.
 */
public final class MatFxUtils {
    private MatFxUtils() {
    }

    /**
     * Convertit une matrice OpenCV en image JavaFX.
     *
     * @param mat matrice OpenCV a convertir
     * @return image JavaFX ou {@code null} si la conversion echoue
     */
    public static Image matToImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }

        MatOfByte buffer = new MatOfByte();
        boolean ok = Imgcodecs.imencode(".png", mat, buffer);

        if (!ok || buffer.empty()) {
            return null;
        }

        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
}
