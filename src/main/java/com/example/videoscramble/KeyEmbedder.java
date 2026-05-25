/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier gere l'embarquement robuste de la cle dans l'image.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;

/**
 * Encode et decode une cle VideoScramble dans une petite zone en haut a gauche.
 *
 * <p>La version stricte du sujet propose les bits faibles du pixel (0,0), mais
 * cette information est detruite par les codecs avec perte comme MP4. Pour la
 * demonstration, chaque bit est donc represente par un bloc clair ou sombre, ce
 * qui resiste mieux a la compression.</p>
 */
public final class KeyEmbedder {
    private static final int KEY_BITS = 15;
    private static final int MAX_BLOCK_SIZE = 10;
    private static final double DARK_VALUE = 24.0;
    private static final double LIGHT_VALUE = 232.0;
    private static final double THRESHOLD = 128.0;

    private KeyEmbedder() {
    }

    /**
     * Inscrit les 15 bits de la cle dans des blocs de pixels robustes.
     *
     * @param image image a modifier directement
     * @param key cle a embarquer
     */
    public static void embed(Mat image, ScrambleKey key) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour embarquer une clé.");
        }
        int blockSize = blockSizeFor(image);

        int packed = pack(key);
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = (packed >> bitIndex) & 1;
            writeBitBlock(image, bitIndex, blockSize, bit);
        }
    }

    /**
     * Lit une cle depuis les blocs de pixels en haut a gauche.
     *
     * @param image image contenant la cle embarquee
     * @return cle extraite de l'image
     */
    public static ScrambleKey extract(Mat image) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour lire une clé embarquée.");
        }
        int blockSize = blockSizeFor(image);

        int packed = 0;
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = readBitBlock(image, bitIndex, blockSize);
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
     * Determine la taille des blocs selon les dimensions de l'image.
     */
    private static int blockSizeFor(Mat image) {
        int blockSize = Math.min(MAX_BLOCK_SIZE, Math.min(image.cols() / KEY_BITS, image.rows()));
        if (blockSize < 1) {
            throw new IllegalArgumentException("Image trop petite pour embarquer une clé.");
        }
        return blockSize;
    }

    /**
     * Ecrit un bit sous forme de bloc clair ou sombre.
     */
    private static void writeBitBlock(Mat image, int bitIndex, int blockSize, int bit) {
        double value = bit == 1 ? LIGHT_VALUE : DARK_VALUE;
        double[] pixel = new double[image.channels()];
        for (int channel = 0; channel < pixel.length; channel++) {
            pixel[channel] = value;
        }

        int startCol = bitIndex * blockSize;
        for (int row = 0; row < blockSize; row++) {
            for (int col = startCol; col < startCol + blockSize; col++) {
                image.put(row, col, pixel);
            }
        }
    }

    /**
     * Relit un bit en comparant la luminosite moyenne du bloc a un seuil.
     */
    private static int readBitBlock(Mat image, int bitIndex, int blockSize) {
        int startCol = bitIndex * blockSize;
        double sum = 0.0;
        int count = 0;
        for (int row = 0; row < blockSize; row++) {
            for (int col = startCol; col < startCol + blockSize; col++) {
                double[] pixel = image.get(row, col);
                sum += (pixel[0] + pixel[1] + pixel[2]) / 3.0;
                count++;
            }
        }
        return (sum / count) >= THRESHOLD ? 1 : 0;
    }
}
