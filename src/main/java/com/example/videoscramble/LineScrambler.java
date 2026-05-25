/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier contient la logique de permutation des lignes d'une image.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

/**
 * Applique et inverse le chiffrement VideoScramble sur une image OpenCV.
 *
 * <p>Les lignes sont traitees par blocs successifs dont la taille est la plus
 * grande puissance de deux possible. Dans chaque bloc, la permutation suit la
 * formule imposee par le sujet : {@code (r + (2s + 1) * idLigne) % size}.</p>
 */
public final class LineScrambler {
    private LineScrambler() {
    }

    /**
     * Chiffre une image en permutant ses lignes.
     *
     * @param input image source a chiffrer
     * @param key cle de chiffrement {@code (r, s)}
     * @return nouvelle image contenant les lignes melangees
     */
    public static Mat scramble(Mat input, ScrambleKey key) {
        return transform(input, key, true);
    }

    /**
     * Dechiffre une image en appliquant la permutation inverse.
     *
     * @param input image chiffree
     * @param key cle de chiffrement utilisee a l'origine
     * @return nouvelle image contenant les lignes remises en ordre
     */
    public static Mat unscramble(Mat input, ScrambleKey key) {
        return transform(input, key, false);
    }

    /**
     * Calcule les tailles des blocs qui seront traites pour une hauteur donnee.
     *
     * @param height hauteur de l'image ou de la video
     * @return liste des tailles de blocs, du haut vers le bas de l'image
     */
    public static List<Integer> blockSizes(int height) {
        List<Integer> blocks = new ArrayList<>();
        int remaining = height;
        while (remaining > 0) {
            int block = highestPowerOfTwoLessOrEqual(remaining);
            blocks.add(block);
            remaining -= block;
        }
        return blocks;
    }

    /**
     * Parcourt l'image de haut en bas et traite chaque bloc independamment.
     */
    private static Mat transform(Mat input, ScrambleKey key, boolean encrypt) {
        Mat output = input.clone();
        int startRow = 0;
        int remaining = input.rows();

        while (remaining > 0) {
            int blockSize = highestPowerOfTwoLessOrEqual(remaining);
            if (blockSize == 1) {
                input.row(startRow).copyTo(output.row(startRow));
                startRow += 1;
                remaining -= 1;
                continue;
            }

            transformBlock(input, output, startRow, blockSize, key, encrypt);
            startRow += blockSize;
            remaining -= blockSize;
        }

        return output;
    }

    /**
     * Applique la permutation directe ou inverse sur un bloc de lignes.
     */
    private static void transformBlock(Mat input, Mat output, int rowOffset, int size, ScrambleKey key, boolean encrypt) {
        int a = key.oddMultiplier() % size;
        int inverseA = modInversePowerOfTwo(a, size);

        for (int localRow = 0; localRow < size; localRow++) {
            int destination;
            if (encrypt) {
                destination = (key.offset() + a * localRow) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + destination));
            } else {
                int source = ((localRow - key.offset()) % size + size) % size;
                source = (inverseA * source) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + source));
            }
        }
    }

    /**
     * Retourne la plus grande puissance de deux inferieure ou egale a la valeur.
     */
    private static int highestPowerOfTwoLessOrEqual(int value) {
        int power = 1;
        while ((power << 1) > 0 && (power << 1) <= value) {
            power <<= 1;
        }
        return power;
    }

    /**
     * Calcule l'inverse modulaire de {@code a} modulo une puissance de deux.
     */
    private static int modInversePowerOfTwo(int a, int modulus) {
        for (int x = 1; x < modulus; x++) {
            if ((a * x) % modulus == 1) {
                return x;
            }
        }
        throw new IllegalArgumentException("Aucun inverse modulaire pour a=" + a + " modulo " + modulus);
    }
}
