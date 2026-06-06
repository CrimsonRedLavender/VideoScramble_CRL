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
