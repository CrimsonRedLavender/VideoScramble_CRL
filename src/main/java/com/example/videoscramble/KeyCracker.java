/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Casse la clé par brute force
 */
public final class KeyCracker {
    private static final int MAX_SCORING_WIDTH = 480;

    private KeyCracker() {
    }

    /**
     * Resultat d'une tentative.
     *
     * @param key meilleure cle trouvee
     * @param score score de l'image
     * @param image image obtenue avec la meilleure cle
     */
    public record CrackResult(ScrambleKey key, double score, Mat image) {}

    /**
     * Teste toutes les cles et conserve celle qui a le score le plus fort.
     *
     * @param scrambled image a dechiffre
     * @return CrackResult (meilleure cle trouvee + l'image decryptee)
     */
    public static CrackResult crack(Mat scrambled) {
        Mat scoringImage = resizeForScoring(scrambled);
        ScrambleKey bestKey = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        try {
            for (int r = 0; r <= 255; r++) {
                for (int s = 0; s <= 127; s++) {
                    ScrambleKey key = new ScrambleKey(r, s);
                    Mat candidate = LineScrambler.unscramble(scoringImage, key);
                    double score = score(candidate);
                    if (score > bestScore) {
                        bestScore = score;
                        bestKey = key;
                    }
                    candidate.release();
                }
            }
        } finally {
            if (scoringImage != scrambled) {
                scoringImage.release();
            }
        }

        Mat bestImage = LineScrambler.unscramble(scrambled, bestKey);
        return new CrackResult(bestKey, bestScore, bestImage);
    }

    /**
     * Reduitla taille de l'image pour accelerer la recherche de la cle.
     */
    private static Mat resizeForScoring(Mat image) {
        if (image.cols() <= MAX_SCORING_WIDTH) {
            return image;
        }

        // La hauteur doit rester identique, car la permutation depend des blocs de lignes.
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(MAX_SCORING_WIDTH, image.rows()), 0, 0, Imgproc.INTER_AREA);
        return resized;
    }

    /**
     * Calcule le score en comparant chaque paire de lignes consecutives.
     */
    private static double score(Mat image) {
        Mat gray = new Mat();
        if (image.channels() == 1) {
            image.convertTo(gray, CvType.CV_64F);
        } else {
            Mat temp = new Mat();
            Imgproc.cvtColor(image, temp, Imgproc.COLOR_BGR2GRAY);
            temp.convertTo(gray, CvType.CV_64F);
            temp.release();
        }

        double total = 0.0;
        for (int row = 0; row < gray.rows() - 1; row++) {
            double[] a = new double[gray.cols()];
            double[] b = new double[gray.cols()];
            gray.get(row, 0, a);
            gray.get(row + 1, 0, b);
            total += -euclidean(a, b);
        }
        gray.release();
        return total;
    }

    /**
     * Calcule la distance euclidienne entre deux lignes en niveaux de gris.
     */
    private static double euclidean(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

}
