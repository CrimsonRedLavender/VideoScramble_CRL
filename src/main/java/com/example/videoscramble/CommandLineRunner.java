/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier contient le mode ligne de commande de l'application.
 */

package com.example.videoscramble;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Path;

/**
 * Execute les traitements demandes en ligne de commande.
 */
public final class CommandLineRunner {
    private CommandLineRunner() {
    }

    /**
     * Analyse les arguments et lance la commande correspondante.
     *
     * @param args arguments passes au programme
     * @return code de sortie conventionnel, {@code 0} en cas de succes
     * @throws Exception si le traitement demande echoue
     */
    public static int run(String[] args) throws Exception {
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            return -1;
        }

        String command = args[0].toLowerCase();
        return switch (command) {
            case "encrypt" -> processVideo(args, Mode.ENCRYPT);
            case "decrypt" -> processVideo(args, Mode.DECRYPT);
            case "crack" -> crack(args);
            case "validate-image" -> validateImage(args);
            case "help", "--help", "-h" -> {
                printUsage();
                yield 0;
            }
            default -> {
                System.err.println("Commande inconnue : " + args[0]);
                printUsage();
                yield 2;
            }
        };
    }

    /**
     * Affiche l'aide du mode ligne de commande.
     */
    public static void printUsage() {
        System.out.println("Usage :");
        System.out.println("  java ... MainApp gui");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s>");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> <r> <s>");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s> --embed-key");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> --embedded-key");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s> --change-key-every <frames> --embed-key");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> <r> <s> --change-key-every <frames>");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp crack <image-ou-video> [sortie-image] [PEARSON|EUCLIDEAN]");
        System.out.println("  java ... MainApp validate-image <image> <r> <s>");
        System.out.println("  java ... MainApp validate-image <image> --key-file <fichier>");
    }

    /**
     * Lance le chiffrement ou le dechiffrement d'une video.
     */
    private static int processVideo(String[] args, Mode mode) throws Exception {
        if (mode == Mode.DECRYPT && hasFlag(args, "--embedded-key")) {
            Path input = Path.of(args[1]);
            Path output = Path.of(args[2]);
            ScrambleKey[] lastPrintedKey = new ScrambleKey[1];
            VideoProcessor.processVideo(input, output, mode, new ScrambleKey(0, 0), false, true, 0, null,
                    usedKey -> {
                        if (!usedKey.equals(lastPrintedKey[0])) {
                            System.out.println("Cle lue : " + usedKey);
                            lastPrintedKey[0] = usedKey;
                        }
                    });
            System.out.println("Dechiffrement termine : " + output);
            return 0;
        }

        if (args.length < 5) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        ScrambleKey key = readKeyArgument(args, 3);
        boolean embedKey = mode == Mode.ENCRYPT && hasFlag(args, "--embed-key");
        int keyChangeInterval = intOption(args, "--change-key-every", 0);

        VideoProcessor.processVideo(input, output, mode, key, embedKey, false, keyChangeInterval, null, null);
        System.out.println((mode == Mode.ENCRYPT ? "Chiffrement" : "Dechiffrement") + " termine : " + output);
        System.out.println("Cle utilisee : " + key);
        if (keyChangeInterval > 0) {
            System.out.println("Changement de cle toutes les " + keyChangeInterval + " frames.");
        }
        if (embedKey) {
            System.out.println("Cle embarquee dans chaque image.");
        }
        return 0;
    }

    /**
     * Cherche la cle d'une image ou d'une video chiffree.
     */
    private static int crack(String[] args) {
        if (args.length < 2) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        Path output = args.length >= 3 && !isScoreMethod(args[2]) ? Path.of(args[2]) : null;
        String methodArgument = output == null && args.length >= 3 ? args[2] : args.length >= 4 ? args[3] : ScoreMethod.PEARSON.name();
        ScoreMethod method = ScoreMethod.valueOf(methodArgument.toUpperCase());

        Mat scrambled = readCrackFrame(input);
        try {
            long start = System.nanoTime();
            ImageCracker.CrackResult result = ImageCracker.crack(scrambled, method);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            try {
                if (output != null) {
                    ensureImageOutput(output);
                    if (!Imgcodecs.imwrite(output.toString(), result.image())) {
                        throw new IllegalStateException("Impossible d'écrire l'image : " + output);
                    }
                }
                System.out.println("Cle trouvee : " + result.key());
                System.out.println("Score : " + result.score());
                System.out.println("Temps : " + elapsedMillis + " ms");
                if (output != null) {
                    System.out.println("Image decryptee : " + output);
                }
            } finally {
                result.image().release();
            }
            return 0;
        } finally {
            scrambled.release();
        }
    }

    /**
     * Verifie sur une image que dechiffrer apres chiffrement retrouve l'original.
     */
    private static int validateImage(String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        ScrambleKey key = readKeyArgument(args, 2);
        Mat image = Imgcodecs.imread(input.toString());
        if (image.empty()) {
            throw new IllegalArgumentException("Impossible de lire l'image : " + input);
        }

        Mat scrambled = LineScrambler.scramble(image, key);
        Mat unscrambled = LineScrambler.unscramble(scrambled, key);
        Mat diff = new Mat();
        Core.absdiff(image, unscrambled, diff);
        double error = Core.sumElems(diff).val[0] + Core.sumElems(diff).val[1] + Core.sumElems(diff).val[2] + Core.sumElems(diff).val[3];

        image.release();
        scrambled.release();
        unscrambled.release();
        diff.release();

        System.out.println("Validation scramble + unscramble : " + (error == 0.0 ? "OK" : "ECHEC"));
        System.out.println("Erreur totale : " + error);
        System.out.println("Cle testee : " + key);
        return error == 0.0 ? 0 : 1;
    }

    /**
     * Lit une cle depuis deux arguments numeriques ou depuis {@code --key-file}.
     */
    private static ScrambleKey readKeyArgument(String[] args, int index) throws Exception {
        if (index < args.length && "--key-file".equalsIgnoreCase(args[index])) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Chemin du fichier cle manquant.");
            }
            return KeyIO.read(Path.of(args[index + 1]));
        }

        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Cle incomplete : r et s sont requis.");
        }
        return new ScrambleKey(Integer.parseInt(args[index]), Integer.parseInt(args[index + 1]));
    }

    /**
     * Lit directement une image ou extrait une frame exploitable depuis une video.
     */
    private static Mat readCrackFrame(Path input) {
        if (isVideoFile(input)) {
            return VideoProcessor.extractRepresentativeFrame(input);
        }

        Mat image = Imgcodecs.imread(input.toString());
        if (image.empty()) {
            throw new IllegalArgumentException("Impossible de lire l'image : " + input);
        }
        return image;
    }

    /**
     * Determine si un argument correspond a une methode de score.
     */
    private static boolean isScoreMethod(String value) {
        for (ScoreMethod method : ScoreMethod.values()) {
            if (method.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine si le fichier semble etre une video selon son extension.
     */
    private static boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".mkv");
    }

    /**
     * Determine si le fichier semble etre une image selon son extension.
     */
    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    /**
     * Verifie que la sortie du cassage est bien une image ecrivable par OpenCV.
     */
    private static void ensureImageOutput(Path output) {
        if (!isImageFile(output)) {
            throw new IllegalArgumentException("La sortie du cassage doit être une image (.png, .jpg, .jpeg ou .bmp) : " + output);
        }
    }

    /**
     * Indique si un drapeau est present dans les arguments.
     */
    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lit une option entiere dans les arguments, ou retourne une valeur par defaut.
     */
    private static int intOption(String[] args, String option, int defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Valeur manquante pour " + option);
                }
                int value = Integer.parseInt(args[i + 1]);
                if (value <= 0) {
                    throw new IllegalArgumentException(option + " doit être strictement positif.");
                }
                return value;
            }
        }
        return defaultValue;
    }
}
