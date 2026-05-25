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

public final class VideoProcessor {
    private VideoProcessor() {
    }

    // prend la video et la coupe en matrices
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

    // prend la video et crypte chaque frame de la video
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

    private static void ensureReadableFile(Path input) {
        if (input == null || !Files.exists(input) || !Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Fichier d'entrée introuvable : " + input);
        }
    }

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
