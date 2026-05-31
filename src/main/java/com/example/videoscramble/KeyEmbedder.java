/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier gere l'embarquement redondant de la cle dans l'image.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;

/**
 * Encode et decode une cle VideoScramble dans les bits de poids faible de l'image.
 *
 * <p>Chaque bit de la cle est repete plusieurs fois dans les LSB des premiers
 * pixels. Au decodage, un vote majoritaire bit par bit permet de limiter l'effet
 * des modifications dues a la compression.</p>
 */
public final class KeyEmbedder {
    private static final int KEY_BITS = 15;
    private static final int REPETITIONS_PER_BIT = 101;

    private KeyEmbedder() {
    }

    /**
     * Inscrit les 15 bits de la cle plusieurs fois dans les LSB de l'image.
     *
     * @param image image a modifier directement
     * @param key cle a embarquer
     */
    public static void embed(Mat image, ScrambleKey key) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour embarquer une clé.");
        }
        ensureCapacity(image);

        int packed = pack(key);
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = (packed >> bitIndex) & 1;
            for (int repetition = 0; repetition < REPETITIONS_PER_BIT; repetition++) {
                writeRepeatedBit(image, bitIndex, repetition, bit);
            }
        }
    }

    /**
     * Lit une cle depuis les LSB repetes et applique un vote majoritaire.
     *
     * @param image image contenant la cle embarquee
     * @return cle extraite de l'image
     */
    public static ScrambleKey extract(Mat image) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour lire une clé embarquée.");
        }
        ensureCapacity(image);

        int packed = 0;
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = readRepeatedBit(image, bitIndex);
            packed |= bit << bitIndex;
        }
        return unpack(packed);
    }

    /**
     * Regroupe {@code r} et {@code s} dans un entier de 15 bits.
     */
    private static int pack(ScrambleKey key) {
        return (key.offset() << 7) | key.step();
    }

    /**
     * Reconstruit une cle depuis un entier de 15 bits.
     */
    private static ScrambleKey unpack(int packed) {
        int offset = (packed >> 7) & 0xFF;
        int step = packed & 0x7F;
        return new ScrambleKey(offset, step);
    }

    /**
     * Verifie que l'image contient assez de canaux pour stocker toutes les repetitions.
     */
    private static void ensureCapacity(Mat image) {
        if ((long) image.rows() * image.cols() * image.channels() < totalStoredBits()) {
            throw new IllegalArgumentException("Image trop petite pour embarquer une clé.");
        }
    }

    /**
     * Ecrit une repetition d'un bit dans le LSB d'un canal.
     */
    private static void writeRepeatedBit(Mat image, int bitIndex, int repetition, int bit) {
        int storedBitIndex = storedBitIndex(bitIndex, repetition);
        PixelChannel position = positionOf(image, storedBitIndex);
        double[] pixel = image.get(position.row(), position.col());
        int value = (int) Math.round(pixel[position.channel()]);
        pixel[position.channel()] = (value & ~1) | bit;
        image.put(position.row(), position.col(), pixel);
    }

    /**
     * Relit un bit par vote majoritaire sur toutes ses repetitions.
     */
    private static int readRepeatedBit(Mat image, int bitIndex) {
        int ones = 0;
        for (int repetition = 0; repetition < REPETITIONS_PER_BIT; repetition++) {
            int storedBitIndex = storedBitIndex(bitIndex, repetition);
            PixelChannel position = positionOf(image, storedBitIndex);
            double[] pixel = image.get(position.row(), position.col());
            ones += ((int) Math.round(pixel[position.channel()])) & 1;
        }
        return ones > REPETITIONS_PER_BIT / 2 ? 1 : 0;
    }

    /**
     * Calcule l'indice global d'une repetition dans la zone de stockage.
     */
    private static int storedBitIndex(int bitIndex, int repetition) {
        return bitIndex * REPETITIONS_PER_BIT + repetition;
    }

    /**
     * Nombre total de LSB utilises pour stocker la cle.
     */
    private static int totalStoredBits() {
        return KEY_BITS * REPETITIONS_PER_BIT;
    }

    /**
     * Convertit un indice de bit stocke en position pixel/canal.
     */
    private static PixelChannel positionOf(Mat image, int storedBitIndex) {
        int channels = image.channels();
        int pixelIndex = storedBitIndex / channels;
        int channel = storedBitIndex % channels;
        int row = pixelIndex / image.cols();
        int col = pixelIndex % image.cols();
        return new PixelChannel(row, col, channel);
    }

    /**
     * Position d'un canal dans une image OpenCV.
     */
    private record PixelChannel(int row, int col, int channel) {}
}
