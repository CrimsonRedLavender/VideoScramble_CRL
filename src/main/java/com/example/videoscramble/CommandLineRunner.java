/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Path;

/**
 * Execute l'application en ligne de commande.
 */
public final class CommandLineRunner {
    private CommandLineRunner() {
    }

    /**
     * Lance la commande correspondante à l'argument.
     *
     * @param args arguments
     * @return code de sortie
     * @throws Exception si le traitement de la commande echoue
     */
    public static int run(String[] args) throws Exception {
        // Aucun argument, ou "gui", signifie que MainApp doit lancer JavaFX.
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            return -1;
        }

        // Le premier argument choisit la fonctionnalite executee en terminal.
        String functionality = args[0].toLowerCase();
        return switch (functionality) {
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
        // Aide volontairement exhaustive pour rappeler les combinaisons d'options possibles.
        System.out.println("Usage :");
        System.out.println("  java ... MainApp gui");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s>");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> <r> <s>");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s> --embed-key");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> --embedded-key");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> <r> <s> --change-key-every <frames> --embed-key");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> <r> <s> --change-key-every <frames>");
        System.out.println("  Option embarquement : --embedding LSB_MAJORITY|ROBUST_BLOCKS");
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp crack <image-ou-video> [sortie-image]");
        System.out.println("  java ... MainApp validate-image <image> <r> <s>");
        System.out.println("  java ... MainApp validate-image <image> --key-file <fichier>");
    }

    /**
     * Lance le chiffrement ou le dechiffrement d'une video.
     */
    private static int processVideo(String[] args, Mode mode) throws Exception {
        // Cas special : en dechiffrement, la cle peut etre relue depuis la video elle-meme.
        if (mode == Mode.DECRYPT && hasFlag(args, "--embedded-key")) {
            Path input = Path.of(args[1]);
            Path output = Path.of(args[2]);

            // Tableau d'une case pour memoriser la derniere cle affichee depuis la lambda.
            ScrambleKey[] lastPrintedKey = new ScrambleKey[1];
            EmbeddingMethod embeddingMethod = embeddingMethodOption(args);

            // La cle (0, 0) est neutre ici : VideoProcessor la remplace par la cle embarquee.
            VideoProcessor.processVideo(input, output, mode, new ScrambleKey(0, 0), false, true, 0, embeddingMethod, null,
                    usedKey -> {
                        // Evite d'imprimer la meme cle pour chaque frame.
                        if (!usedKey.equals(lastPrintedKey[0])) {
                            System.out.println("Cle lue : " + usedKey);
                            lastPrintedKey[0] = usedKey;
                        }
                    });
            System.out.println("Dechiffrement termine : " + output);
            return 0;
        }

        // Les modes classiques ont besoin de : commande, entree, sortie, r, s.
        if (args.length < 5) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        ScrambleKey key = readKeyArgument(args, 3);
        boolean embedKey = mode == Mode.ENCRYPT && hasFlag(args, "--embed-key");
        int keyChangeInterval = intOption(args, "--change-key-every", 0);
        EmbeddingMethod embeddingMethod = embeddingMethodOption(args);

        // Pas de preview en CLI : les deux callbacks d'affichage restent a null.
        VideoProcessor.processVideo(input, output, mode, key, embedKey, false, keyChangeInterval, embeddingMethod, null, null);
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
     * Cherche la cle d'une video chiffree.
     */
    private static int crack(String[] args) {
        // La sortie image est optionnelle : la commande peut seulement afficher la cle trouvee.
        if (args.length < 2) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        Path output = args.length >= 3 ? Path.of(args[2]) : null;

        // On accepte soit une image deja extraite, soit une video dont on extrait une frame.
        Mat scrambled = readCrackFrame(input);
        try {
            long start = System.nanoTime();
            KeyCracker.CrackResult result = KeyCracker.crack(scrambled);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            try {
                if (output != null) {
                    // Le crack produit une image decryptee fixe.
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
                // L'image resultat appartient au CrackResult et doit etre liberee ici.
                result.image().release();
            }
            return 0;
        } finally {
            // La frame source est liberee meme si le crack echoue.
            scrambled.release();
        }
    }

    /**
     * Verifie sur une image que dechiffrer apres chiffrement retrouve l'original.
     */
    private static int validateImage(String[] args) throws Exception {
        // Validation rapide : scramble puis unscramble doivent redonner exactement l'image.
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

        // On compare l'image originale avec le resultat du cycle complet.
        Mat scrambled = LineScrambler.scramble(image, key);
        Mat unscrambled = LineScrambler.unscramble(scrambled, key);
        Mat diff = new Mat();
        Core.absdiff(image, unscrambled, diff);

        // Une somme nulle signifie que chaque canal de chaque pixel est identique.
        double error = Core.sumElems(diff).val[0] + Core.sumElems(diff).val[1] + Core.sumElems(diff).val[2] + Core.sumElems(diff).val[3];

        // Liberation explicite des matrices OpenCV creees pour la validation.
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
        // La cle peut venir d'un fichier texte via --key-file.
        if (index < args.length && "--key-file".equalsIgnoreCase(args[index])) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Chemin du fichier cle manquant.");
            }
            return KeyIO.read(Path.of(args[index + 1]));
        }

        // Sinon, deux entiers consecutifs representent r puis s.
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Cle incomplete : r et s sont requis.");
        }
        return new ScrambleKey(Integer.parseInt(args[index]), Integer.parseInt(args[index + 1]));
    }

    /**
     * Lit directement une image ou extrait une frame exploitable depuis une video.
     */
    private static Mat readCrackFrame(Path input) {
        // Pour une video, le crack travaille sur une frame representative extraite par VideoProcessor.
        if (isVideoFile(input)) {
            return VideoProcessor.extractRepresentativeFrame(input);
        }

        // Pour une image, on lit directement le fichier avec OpenCV.
        Mat image = Imgcodecs.imread(input.toString());
        if (image.empty()) {
            throw new IllegalArgumentException("Impossible de lire l'image : " + input);
        }
        return image;
    }

    /**
     * Determine si le fichier semble etre une video selon son extension.
     */
    private static boolean isVideoFile(Path path) {
        // Detection simple par extension, suffisante pour choisir la methode de lecture.
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".mkv");
    }

    /**
     * Determine si le fichier semble etre une image selon son extension.
     */
    private static boolean isImageFile(Path path) {
        // Ces extensions sont celles que l'on accepte pour ecrire le resultat du crack.
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    /**
     * Verifie que la sortie du cassage est bien une image ecrivable par OpenCV.
     */
    private static void ensureImageOutput(Path output) {
        // Evite d'essayer d'ecrire une image avec une extension video.
        if (!isImageFile(output)) {
            throw new IllegalArgumentException("La sortie du cassage doit être une image (.png, .jpg, .jpeg ou .bmp) : " + output);
        }
    }

    /**
     * Indique si un drapeau est present dans les arguments.
     */
    private static boolean hasFlag(String[] args, String flag) {
        // Les drapeaux sont compares sans tenir compte de la casse.
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
        // Parcourt les arguments pour trouver une option suivie d'un entier positif.
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

        // Option absente : on garde la valeur par defaut fournie par l'appelant.
        return defaultValue;
    }

    /**
     * Lit la methode d'embarquement demandee, ROBUST_BLOCKS par defaut pour la demonstration.
     */
    private static EmbeddingMethod embeddingMethodOption(String[] args) {
        // Permet de choisir explicitement la methode d'embarquement depuis le terminal.
        for (int i = 0; i < args.length; i++) {
            if ("--embedding".equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Valeur manquante pour --embedding");
                }
                return EmbeddingMethod.valueOf(args[i + 1].toUpperCase());
            }
        }

        // Methode robuste par defaut, adaptee aux videos MP4 de demonstration.
        return EmbeddingMethod.LUM_BLOCKS;
    }
}
