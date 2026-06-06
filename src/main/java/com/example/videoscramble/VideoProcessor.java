/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import org.opencv.core.Core;
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
 * Organose le traitement d'une video.
 */
public final class VideoProcessor {
    private static final double MIN_FRAME_BRIGHTNESS = 3.0;


    /**
     * Lit les dimensions d'une video et retourne les tailles de blocs qui seront
     * utilisees pour la permutation des lignes.
     *
     * @param input chemin de la video
     * @return tailles des blocs de lignes
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
     * Extrait une image pas noire pour casser la cle.
     *
     * @param input chemin de la video a analyser
     * @return frame pas noire, ou premiere frame lue si toute la video est sombre
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
     * Traite une video.
     *
     * @param input chemin de la video source
     * @param output chemin de la video de sortie
     * @param mode fonctionnalite de traitement a appliquer
     * @param key cle de chiffrement ou de dechiffrement
     * @param embedKey option pour ecrire la cle dans chaque frame chiffree
     * @param readEmbeddedKey option pour lire la cle embarquee dans chaque frame
     * @param keyChangeInterval nombre de frames entre deux changements de cle
     * @param embeddingMethod methode pour ecrire ou lire la cle embarquee
     * @param onFrame utilise par l'IHM pour afficher l'apercu
     * @param onKey callback appele quand une cle est utilisee
     * @throws IOException si le dossier de sortie ne peut pas etre cree
     */
    public static void processVideo(Path input,
                                    Path output,
                                    Mode mode,
                                    ScrambleKey key,
                                    boolean embedKey,
                                    boolean readEmbeddedKey,
                                    int keyChangeInterval,
                                    EmbeddingMethod embeddingMethod,
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
        long frameIndex = 0;
        try {
            while (capture.read(frame)) {
                if (frame.empty()) {
                    continue;
                }
                ScrambleKey scheduledKey = scheduledKey(key, frameIndex, keyChangeInterval);
                ScrambleKey frameKey = mode == Mode.DECRYPT && readEmbeddedKey ? KeyEmbedder.extract(frame, embeddingMethod) : scheduledKey;
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
                    KeyEmbedder.embed(processed, frameKey, embeddingMethod);
                }

                if (onFrame != null) {
                    onFrame.accept(frame.clone(), processed.clone());
                }

                writer.write(processed);
                processed.release();
                frameIndex++;
            }
        } finally {
            frame.release();
            writer.release();
            capture.release();
        }
    }

    /**
     * Verifie que le fichier d'entree existe et peut etre lu
     */
    private static void ensureReadableFile(Path input) {
        if (input == null || !Files.exists(input) || !Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Fichier d'entrée introuvable : " + input);
        }
    }

    /**
     * Cree le dossier parent du fichier de sortie s'il n'existe pas.
     */
    private static void ensureParentDirectory(Path output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Chemin du fichier de sortie est requis.");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Calcule une nouvelle cle pour la change en cours de video.
     */
    private static ScrambleKey scheduledKey(ScrambleKey baseKey, long frameIndex, int keyChangeInterval) {
        if (keyChangeInterval <= 0) {
            return baseKey;
        }

        long group = frameIndex / keyChangeInterval;
        int offset = (int) ((baseKey.offset() + 73L * group) % 256);
        int step = (int) ((baseKey.step() + 37L * group) % 128);
        return new ScrambleKey(offset, step);
    }

    /**
     * Choisit un codec en fonction de l'extension du fichier de sortie.
     */
    private static int fourccFor(Path output) {
        String name = output.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) {
            return VideoWriter.fourcc('m', 'p', '4', 'v');
        }
        if (name.endsWith(".avi")) {
            return VideoWriter.fourcc('M', 'J', 'P', 'G');
        }
        throw new IllegalArgumentException("Extension vidéo de sortie non supportée : " + output);
    }

    /**
     * Calcule le niveau de gris moyen d'une image pour eviter les frames noires.
     */
    private static double meanBrightness(Mat frame) {
        Mat gray = new Mat();
        try {
            if (frame.channels() == 1) { // si elle n'a qu'un channel, c'est deja une image grayscale
                gray = frame;
            } else {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            }
            Scalar mean = Core.mean(gray); // calcul la moyenne de tous les pixels
            return mean.val[0];
        } finally {
            if (gray != frame) { // free l'espace memoire de gray si ce n'est pas une copie de frame
                gray.release();
            }
        }
    }
}
