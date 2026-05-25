/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier gere la lecture, le traitement et l'ecriture des videos.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Orchestre le traitement video image par image avec OpenCV.
 */
public final class VideoProcessor {
    private VideoProcessor() {
    }

    /**
     * Lit les dimensions d'une video et retourne les tailles de blocs qui seront
     * utilisees par l'algorithme de permutation des lignes.
     *
     * @param input chemin de la video a inspecter
     * @return tailles des blocs de lignes traites successivement
     */
    public static List<Integer> probeBlocks(Path input) {
        VideoCapture capture = new VideoCapture(input.toString());
        if (!capture.isOpened()) {
            throw new IllegalArgumentException("Impossible d'ouvrir la vidéo : " + input);
        }
        try {
            int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            return LineScrambler.blockSizes(height);
        } finally {
            capture.release();
        }
    }

    /**
     * Traite une video complete en chiffrant ou dechiffrant chaque image.
     *
     * @param input chemin de la video source
     * @param output chemin de la video de sortie
     * @param mode type de traitement a appliquer
     * @param key cle de chiffrement ou de dechiffrement
     * @param onFrame callback optionnel utilise par l'IHM pour afficher l'apercu
     * @throws IOException si le dossier de sortie ne peut pas etre cree
     */
    public static void processVideo(Path input,
                                    Path output,
                                    Mode mode,
                                    ScrambleKey key,
                                    BiConsumer<Mat, Mat> onFrame) throws IOException {
        ensureReadableFile(input);
        ensureParentDirectory(output);

        VideoCapture capture = new VideoCapture(input.toString());
        if (!capture.isOpened()) {
            throw new IllegalArgumentException("Impossible d'ouvrir la vidéo : " + input);
        }

        int width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        double fps = capture.get(Videoio.CAP_PROP_FPS);
        if (fps <= 0) {
            fps = 25.0;
        }

        VideoWriter writer = new VideoWriter(
                output.toString(),
                VideoWriter.fourcc('m', 'p', '4', 'v'),
                fps,
                new Size(width, height)
        );

        if (!writer.isOpened()) {
            capture.release();
            throw new IllegalStateException("Impossible d'écrire la vidéo de sortie : " + output);
        }

        Mat frame = new Mat();
        try {
            while (capture.read(frame)) {
                if (frame.empty()) {
                    continue;
                }
                Mat processed = mode == Mode.ENCRYPT
                        ? LineScrambler.scramble(frame, key)
                        : LineScrambler.unscramble(frame, key);

                if (processed == null || processed.empty()) {
                    continue;
                }

                if (onFrame != null) {
                    onFrame.accept(frame.clone(), processed.clone());
                }

                writer.write(processed);
                processed.release();
            }
        } finally {
            frame.release();
            writer.release();
            capture.release();
        }
    }

    /**
     * Verifie que le fichier d'entree existe et peut etre lu comme fichier.
     */
    private static void ensureReadableFile(Path input) {
        if (input == null || !Files.exists(input) || !Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Fichier d'entrée introuvable : " + input);
        }
    }

    /**
     * Cree le dossier parent du fichier de sortie si necessaire.
     */
    private static void ensureParentDirectory(Path output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Chemin de sortie requis.");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
