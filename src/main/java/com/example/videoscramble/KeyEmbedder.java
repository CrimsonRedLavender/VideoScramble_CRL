/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 * Cette classe gere l'embarquement de la cle dans l'image.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;

/**
 * Encode et decode une cle dans les bits de poids faible de l'image.
 */
public final class KeyEmbedder {
    private static final int KEY_BITS = 15;
    private static final int REPETITIONS_PER_BIT = 101;
    private static final int MAX_BLOCK_SIZE = 10;
    private static final double DARK_VALUE = 24.0;
    private static final double LIGHT_VALUE = 232.0;
    private static final double SEUIL = 128.0;

    private KeyEmbedder() {
    }

    /**
     * Ecrit les 15 bits de la cle plusieurs fois dans les LSB de l'image.
     *
     * @param image image qui doit embarquer la cle
     * @param key cle a embarquer
     */
    public static void embed(Mat image, ScrambleKey key) {
        embed(image, key, EmbeddingMethod.LSB_MAJORITY);
    }

    /**
     * Ecrit les 15 bits de la cle.
     *
     * @param image image qui doit embarquer la cle
     * @param key cle a embarquer
     * @param method methode d'embarquement utilisee
     */
    public static void embed(Mat image, ScrambleKey key, EmbeddingMethod method) {
        // L'embarquement modifie les canaux couleur, donc une image couleur est obligatoire.
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour embarquer une clé.");
        }

        // Methode robuste : on ecrit des blocs tres sombres ou tres clairs dans le haut de l'image.
        if (method == EmbeddingMethod.LUM_BLOCKS) {
            embedLumBlocks(image, key);
            return;
        }

        // Methode LSB : il faut assez de canaux pour stocker les 15 bits repetes.
        ensureCapacity(image);

        // La cle est compactee en 15 bits : 8 bits pour r, 7 bits pour s.
        int packed = pack(key);
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            int bit = (packed >> bitIndex) & 1;

            // Chaque bit est repete pour pouvoir corriger de petites alterations par vote.
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
        // La lecture utilise aussi les canaux couleur de l'image.
        if (image == null || image.empty() || image.channels() < 3) {
            throw new IllegalArgumentException("Image couleur requise pour lire une clé embarquée.");
        }

        // Lecture de la methode par blocs de luminance.
        if (method == EmbeddingMethod.LUM_BLOCKS) {
            return extractLumBlocks(image);
        }

        // Lecture de la methode LSB avec repetitions.
        ensureCapacity(image);

        int packed = 0;
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            // Chaque bit final vient d'un vote majoritaire sur ses repetitions.
            int bit = readRepeatedBit(image, bitIndex);
            packed |= bit << bitIndex;
        }
        return unpack(packed);
    }

    /**
     * Regroupe une cle dans un entier de 15 bits.
     */
    private static int pack(ScrambleKey key) {
        // r occupe les 8 bits de poids fort, s les 7 bits de poids faible.
        return (key.offset() << 7) | key.step();
    }

    /**
     * Reconstruit une cle d'un entier de 15 bits.
     */
    private static ScrambleKey unpack(int packed) {
        // Les masques isolent exactement les tailles autorisees par ScrambleKey.
        int offset = (packed >> 7) & 0xFF;
        int step = packed & 0x7F;
        return new ScrambleKey(offset, step);
    }

    /**
     * Verifie que l'image contient assez de canaux pour stocker toutes les repetitions.
     */
    private static void ensureCapacity(Mat image) {
        // rows * cols * channels donne le nombre de canaux disponibles pour des bits LSB.
        if ((long) image.rows() * image.cols() * image.channels() < totalStoredBits()) {
            throw new IllegalArgumentException("Image trop petite pour embarquer une cle.");
        }
    }

    /**
     * Ecrit une repetition d'un bit dans le LSB d'un canal.
     */
    private static void writeRepeatedBit(Mat image, int bitIndex, int repetition, int bit) {
        // Conversion de l'indice logique du bit vers un pixel et un canal OpenCV.
        int storedBitIndex = storedBitIndex(bitIndex, repetition);
        PixelChannel position = positionOf(image, storedBitIndex);
        double[] pixel = image.get(position.row(), position.col());

        // Remplace uniquement le bit de poids faible du canal choisi.
        int value = (int) Math.round(pixel[position.channel()]);
        pixel[position.channel()] = (value & ~1) | bit;
        image.put(position.row(), position.col(), pixel);
    }

    /**
     * Relit un bit par vote majoritaire.
     */
    private static int readRepeatedBit(Mat image, int bitIndex) {
        int ones = 0;
        for (int repetition = 0; repetition < REPETITIONS_PER_BIT; repetition++) {
            // On relit les memes emplacements que ceux utilises lors de l'ecriture.
            int storedBitIndex = storedBitIndex(bitIndex, repetition);
            PixelChannel position = positionOf(image, storedBitIndex);
            double[] pixel = image.get(position.row(), position.col());
            ones += ((int) Math.round(pixel[position.channel()])) & 1;
        }

        // Plus de la moitie de 1 signifie que le bit encode etait 1.
        return ones > REPETITIONS_PER_BIT / 2 ? 1 : 0;
    }

    /**
     * Calcule l'indice d'une repetition dans la zone de stockage.
     */
    private static int storedBitIndex(int bitIndex, int repetition) {
        // Les repetitions d'un meme bit sont stockees consecutivement.
        return bitIndex * REPETITIONS_PER_BIT + repetition;
    }

    /**
     * Nombre total de LSB pour stocker la cle.
     */
    private static int totalStoredBits() {
        // 15 bits de cle multiplies par leur nombre de repetitions.
        return KEY_BITS * REPETITIONS_PER_BIT;
    }

    /**
     * Convertit un indice de bit stocke en position pixel/canal.
     */
    private static PixelChannel positionOf(Mat image, int storedBitIndex) {
        // Les canaux sont parcourus lineairement : B, G, R, puis pixel suivant.
        int channels = image.channels();
        int pixelIndex = storedBitIndex / channels;
        int channel = storedBitIndex % channels;
        int row = pixelIndex / image.cols();
        int col = pixelIndex % image.cols();
        return new PixelChannel(row, col, channel);
    }

    /**
     * Position d'un canal dans une image.
     */
    private record PixelChannel(int row, int col, int channel) {}

    /**
     * Encode chaque bit dans un bloc clair ou sombre.
     */
    private static void embedLumBlocks(Mat image, ScrambleKey key) {
        // La taille de bloc depend de l'image pour garantir 15 blocs visibles et lisibles.
        int blockSize = blockSizeFor(image);
        int packed = pack(key);
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            // Un bloc clair represente 1, un bloc sombre represente 0.
            int bit = (packed >> bitIndex) & 1;
            writeBitBlock(image, bitIndex, blockSize, bit);
        }
    }

    /**
     * Decode la cle depuis des blocs clairs ou sombres.
     */
    private static ScrambleKey extractLumBlocks(Mat image) {
        // La meme taille de bloc doit etre retrouvee a la lecture.
        int blockSize = blockSizeFor(image);
        int packed = 0;
        for (int bitIndex = 0; bitIndex < KEY_BITS; bitIndex++) {
            // Chaque bloc est converti en bit par moyenne de luminance.
            int bit = readBitBlock(image, bitIndex, blockSize);
            packed |= bit << bitIndex;
        }
        return unpack(packed);
    }

    /**
     * Determine la taille des blocs selon les dimensions de l'image.
     */
    private static int blockSizeFor(Mat image) {
        // On limite la taille pour ne pas masquer une trop grande zone de l'image.
        int blockSize = Math.min(MAX_BLOCK_SIZE, Math.min(image.cols() / KEY_BITS, image.rows()));
        if (blockSize < 1) {
            throw new IllegalArgumentException("Image trop petite pour embarquer une clé.");
        }
        return blockSize;
    }

    /**
     * Ecrit un bit en bloc clair ou sombre.
     */
    private static void writeBitBlock(Mat image, int bitIndex, int blockSize, int bit) {
        // Valeur uniforme sur tous les canaux pour obtenir un bloc gris clair ou sombre.
        double value = bit == 1 ? LIGHT_VALUE : DARK_VALUE;
        double[] pixel = new double[image.channels()];
        for (int channel = 0; channel < pixel.length; channel++) {
            pixel[channel] = value;
        }

        // Les 15 blocs sont places cote a cote sur la premiere ligne de blocs.
        int startCol = bitIndex * blockSize;
        for (int row = 0; row < blockSize; row++) {
            for (int col = startCol; col < startCol + blockSize; col++) {
                image.put(row, col, pixel);
            }
        }
    }

    /**
     * Relit un bit en comparant la luminosite moyenne du bloc au seuil.
     */
    private static int readBitBlock(Mat image, int bitIndex, int blockSize) {
        // Le bloc lu correspond a la zone ecrite pour ce bit.
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

        // La moyenne du bloc decide si le bit est sombre (0) ou clair (1).
        return (sum / count) >= SEUIL ? 1 : 0;
    }
}
