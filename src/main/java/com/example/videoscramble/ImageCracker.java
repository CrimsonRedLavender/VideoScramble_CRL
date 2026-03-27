package com.example.videoscramble;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public final class ImageCracker {
    private ImageCracker() {
    }

    public record CrackResult(ScrambleKey key, double score, Mat image) {}

    public static CrackResult crack(Mat scrambled, ScoreMethod method) {
        Mat bestImage = null;
        ScrambleKey bestKey = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int r = 0; r <= 255; r++) {
            for (int s = 0; s <= 127; s++) {
                ScrambleKey key = new ScrambleKey(r, s);
                Mat candidate = LineScrambler.unscramble(scrambled, key);
                double score = score(candidate, method);
                if (score > bestScore) {
                    if (bestImage != null) {
                        bestImage.release();
                    }
                    bestScore = score;
                    bestKey = key;
                    bestImage = candidate.clone();
                }
                candidate.release();
            }
        }

        return new CrackResult(bestKey, bestScore, bestImage);
    }

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

    private static double euclidean(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

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

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
