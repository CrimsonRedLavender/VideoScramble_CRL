/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

/**
 * Applique les permutations des lignes.
 *
 */
public final class LineScrambler {
    private LineScrambler() {
    }

    /**
     * Chiffre une image en permutant ses lignes.
     *
     * @param input image source a chiffrer
     * @param key cle de chiffrement
     * @return image contenant les lignes melangees
     */
    public static Mat scramble(Mat input, ScrambleKey key) {
        return transform(input, key, true);
    }

    /**
     * Dechiffre une image en appliquant la permutation inverse.
     *
     * @param input image chiffree
     * @param key cle de chiffrement originale
     * @return image contenant les lignes remises dans leur ordre
     */
    public static Mat unscramble(Mat input, ScrambleKey key) {
        return transform(input, key, false);
    }

    /**
     * Calcule les tailles des blocs qui seront traites pour une hauteur donnee.
     *
     * @param height hauteur
     * @return liste des tailles des blocs
     */
    public static List<Integer> blockSizes(int height) {
        List<Integer> blocks = new ArrayList<>();
        int remaining = height;
        while (remaining > 0) {
            // Chaque bloc prend la plus grande puissance de deux encore disponible.
            int block = highestPowerOfTwoLessOrEqual(remaining);
            blocks.add(block);
            remaining -= block;
        }
        return blocks;
    }

    /**
     * Parcourt l'image de haut en bas et traite chaque bloc.
     */
    private static Mat transform(Mat input, ScrambleKey key, boolean encrypt) {
        // On clone l'image pour conserver les pixels et les dimensions de depart.
        Mat output = input.clone();
        int startRow = 0;
        int remaining = input.rows();

        while (remaining > 0) {
            // La permutation modulaire est simple uniquement sur des tailles puissance de deux.
            int blockSize = highestPowerOfTwoLessOrEqual(remaining);
            if (blockSize == 1) {
                // Un bloc d'une seule ligne ne peut pas etre permute.
                input.row(startRow).copyTo(output.row(startRow));
                startRow += 1;
                remaining -= 1;
                continue;
            }

            // Le bloc courant est traite independamment des autres blocs.
            transformBlock(input, output, startRow, blockSize, key, encrypt);
            startRow += blockSize;
            remaining -= blockSize;
        }

        return output;
    }

    /**
     * Applique la permutation sur un bloc de lignes.
     */
    private static void transformBlock(Mat input, Mat output, int rowOffset, int size, ScrambleKey key, boolean encrypt) {
        // Le multiplicateur doit etre impair pour avoir un inverse modulo une puissance de deux.
        int a = key.oddMultiplier() % size;
        int inverseA = modInversePowerOfTwo(a, size);

        for (int localRow = 0; localRow < size; localRow++) {
            int destination;
            if (encrypt) {
                // Chiffrement : chaque ligne source est envoyee vers une position calculee.
                destination = (key.offset() + a * localRow) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + destination));
            } else {
                // Dechiffrement : on applique l'inverse de la formule de chiffrement.
                int source = ((localRow - key.offset()) % size + size) % size;
                source = (inverseA * source) % size;
                input.row(rowOffset + localRow).copyTo(output.row(rowOffset + source));
            }
        }
    }

    /**
     * Calcule la plus grande puissance de deux inferieure ou egale a la valeur.
     */
    private static int highestPowerOfTwoLessOrEqual(int value) {
        int power = 1;
        while ((power << 1) > 0 && (power << 1) <= value) {
            // Double la puissance jusqu'a depasser la valeur demandee.
            power <<= 1;
        }
        return power;
    }

    /**
     * Calcule l'inverse modulaire de a modulo une puissance de deux.
     */
    private static int modInversePowerOfTwo(int a, int modulus) {
        // Recherche directe suffisante car les blocs restent limites par la hauteur de l'image.
        for (int x = 1; x < modulus; x++) {
            if ((a * x) % modulus == 1) {
                return x;
            }
        }
        throw new IllegalArgumentException("Aucun inverse modulaire existe pour a=" + a + " modulo " + modulus);
    }
}
