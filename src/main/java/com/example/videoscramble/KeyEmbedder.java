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
    private static final int MAX_BLOCK_SIZE = 10;
    private static final double DARK_VALUE = 24.0;
    private static final double LIGHT_VALUE = 232.0;
    private static final double THRESHOLD = 128.0;

    private KeyEmbedder() {
    }

    /**
     * Inscrit les 15 bits de la cle plusieurs fois dans les LSB de l'image.
     *
     * @param image image a modifier directement
     * @param key cle a embarquer
     */
    public static void embed(Mat image, ScrambleKey key) {
        embed(image, key, EmbeddingMethod.LSB_MAJORITY);
    }

    /**
     * Inscrit les 15 bits de la cle avec la methode demandee.
     *
     * @param image image a modifier directement
     * @param key cle a embarquer
     * @param method methode d'embarquement utilisee
     */
    public static void embed(Mat image, ScrambleKey key, EmbeddingMethod method) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour embarquer une clé.");
        }
        if (method == EmbeddingMethod.LUM_BLOCKS) {
            embedRobustBlocks(image, key);
            return;
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
        return extract(image, EmbeddingMethod.LSB_MAJORITY);
    }

    /**
     * Lit une cle embarquee avec la methode demandee.
     *
     * @param image image contenant la cle embarquee
     * @param method methode de lecture utilisee
     * @return cle extraite de l'image
     */
    public static ScrambleKey extract(Mat image, EmbeddingMethod method) {
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour lire une clé embarquée.");
        }
        if (method == EmbeddingMethod.LUM_BLOCKS) {
            return extractRobustBlocks(image);
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

    /**
     * Encode chaque bit dans un bloc clair ou sombre, robuste a la compression MP4.
     */
    private static void embedRobustBlocks(Mat image, ScrambleKey key) {
        int blockSize = blockSizeFor(image);
        int packed = pack(key);
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = (packed >> bitIndex) & 1;
            writeBitBlock(image, bitIndex, blockSize, bit);
        }
    }

    /**
     * Decode la cle depuis des blocs clairs ou sombres.
     */
    private static ScrambleKey extractRobustBlocks(Mat image) {
        int blockSize = blockSizeFor(image);
        int packed = 0;
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = readBitBlock(image, bitIndex, blockSize);
            packed |= bit << bitIndex;
        }
        return unpack(packed);
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
