/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier gere la lecture, le traitement et l'ecriture des videos.
 */

package com.example.videoscramble;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Orchestre le traitement video image par image avec OpenCV.
 */
public final class VideoProcessor {
    private static final double MIN_FRAME_BRIGHTNESS = 3.0;

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
     * Extrait une image utilisable pour casser la cle depuis une video.
     *
     * <p>Les premieres images d'une video sont parfois noires. Cette methode
     * parcourt donc le debut de la video et conserve la premiere frame dont la
     * luminosite moyenne depasse un seuil minimal.</p>
     *
     * @param input chemin de la video a analyser
     * @return frame non noire, ou premiere frame lue si toute la video est sombre
     */
    public static Mat extractRepresentativeFrame(Path input) {
        ensureReadableFile(input);
        VideoCapture capture = new VideoCapture(input.toString());
        if (!capture.isOpened()) {
            throw new IllegalArgumentException("Impossible d'ouvrir la vidéo : " + input);
        }

        Mat frame = new Mat();
        Mat fallback = null;
        try {
            while (capture.read(frame)) {
                if (frame.empty()) {
                    continue;
                }
                if (fallback == null) {
                    fallback = frame.clone();
                }
                if (meanBrightness(frame) >= MIN_FRAME_BRIGHTNESS) {
                    Mat selected = frame.clone();
                    if (fallback != null) {
                        fallback.release();
                    }
                    return selected;
                }
            }
        } finally {
            frame.release();
            capture.release();
        }

        if (fallback != null) {
            return fallback;
        }
        throw new IllegalArgumentException("Aucune image lisible dans la vidéo : " + input);
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
        processVideo(input, output, mode, key, false, false, onFrame, null);
    }

    /**
     * Traite une video complete avec option d'embarquement ou de lecture de cle.
     *
     * @param input chemin de la video source
     * @param output chemin de la video de sortie
     * @param mode type de traitement a appliquer
     * @param key cle par defaut, utilisee si aucune cle embarquee n'est lue
     * @param embedKey true pour ecrire la cle dans chaque image chiffree
     * @param readEmbeddedKey true pour lire la cle depuis chaque image avant dechiffrement
     * @param onFrame callback optionnel utilise par l'IHM pour afficher l'apercu
     * @param onKey callback optionnel appele quand une cle est utilisee
     * @throws IOException si le dossier de sortie ne peut pas etre cree
     */
    public static void processVideo(Path input,
                                    Path output,
                                    Mode mode,
                                    ScrambleKey key,
                                    boolean embedKey,
                                    boolean readEmbeddedKey,
                                    BiConsumer<Mat, Mat> onFrame,
                                    Consumer<ScrambleKey> onKey) throws IOException {
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
                fourccFor(output),
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
                ScrambleKey frameKey = mode == Mode.DECRYPT && readEmbeddedKey ? KeyEmbedder.extract(frame) : key;
                if (onKey != null) {
                    onKey.accept(frameKey);
                }

                Mat processed = mode == Mode.ENCRYPT
                        ? LineScrambler.scramble(frame, frameKey)
                        : LineScrambler.unscramble(frame, frameKey);

                if (processed == null || processed.empty()) {
                    continue;
                }

                if (mode == Mode.ENCRYPT && embedKey) {
                    KeyEmbedder.embed(processed, frameKey);
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

    /**
     * Choisit un codec adapte au fichier de sortie.
     */
    private static int fourccFor(Path output) {
        String name = output.getFileName().toString().toLowerCase();
        if (name.endsWith(".mkv")) {
            return VideoWriter.fourcc('F', 'F', 'V', '1');
        }
        if (name.endsWith(".avi")) {
            return VideoWriter.fourcc('M', 'J', 'P', 'G');
        }
        return VideoWriter.fourcc('m', 'p', '4', 'v');
    }

    /**
     * Calcule la luminosite moyenne d'une image pour eviter les frames noires.
     */
    private static double meanBrightness(Mat frame) {
        Mat gray = new Mat();
        try {
            if (frame.channels() == 1) {
                gray = frame;
            } else {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            }
            Scalar mean = org.opencv.core.Core.mean(gray);
            return mean.val[0];
        } finally {
            if (gray != frame) {
                gray.release();
            }
        }
    }
}
