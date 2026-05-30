/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier contient la recherche de cle par force brute sur une image.
 */

package com.example.videoscramble;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Essaie toutes les cles possibles pour retrouver celle qui decrypte le mieux
 * une image chiffree avec VideoScramble.
 */
public final class ImageCracker {
    private static final int MAX_SCORING_WIDTH = 480;

    private ImageCracker() {
    }

    /**
     * Resultat d'une tentative de cassage de cle.
     *
     * @param key meilleure cle trouvee
     * @param score score associe a l'image decryptee
     * @param image image obtenue avec la meilleure cle
     */
    public record CrackResult(ScrambleKey key, double score, Mat image) {}

    /**
     * Teste les 32768 cles possibles et conserve celle qui maximise le score.
     *
     * @param scrambled image chiffree a analyser
     * @param method critere de score utilise pour comparer les lignes voisines
     * @return meilleure cle trouvee avec l'image decryptee correspondante
     */
    public static CrackResult crack(Mat scrambled, ScoreMethod method) {
        Mat scoringImage = resizeForScoring(scrambled);
        ScrambleKey bestKey = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        try {
            for (int r = 0; r <= 255; r++) {
                for (int s = 0; s <= 127; s++) {
                    ScrambleKey key = new ScrambleKey(r, s);
                    Mat candidate = LineScrambler.unscramble(scoringImage, key);
                    double score = score(candidate, method);
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
     * Reduit l'image analysee pour accelerer la recherche exhaustive de cle.
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
     * Calcule un score global en comparant chaque paire de lignes consecutives.
     */
    private static double score(Mat image, ScoreMethod method) {
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
            total += method == ScoreMethod.PEARSON ? pearson(a, b) : -euclidean(a, b);
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

    /**
     * Calcule la correlation de Pearson entre deux lignes en niveaux de gris.
     */
    private static double pearson(double[] a, double[] b) {
        double meanA = mean(a);
        double meanB = mean(b);

        double num = 0.0;
        double denA = 0.0;
        double denB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            num += da * db;
            denA += da * da;
            denB += db * db;
        }
        if (denA == 0.0 || denB == 0.0) {
            return -1.0;
        }
        return num / Math.sqrt(denA * denB);
    }

    /**
     * Calcule la moyenne des valeurs d'une ligne.
     */
    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
