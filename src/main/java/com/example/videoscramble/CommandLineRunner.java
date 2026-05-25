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
        System.out.println("  java ... MainApp encrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp decrypt <entree-video> <sortie-video> --key-file <fichier>");
        System.out.println("  java ... MainApp validate-image <image> <r> <s>");
        System.out.println("  java ... MainApp validate-image <image> --key-file <fichier>");
    }

    /**
     * Lance le chiffrement ou le dechiffrement d'une video.
     */
    private static int processVideo(String[] args, Mode mode) throws Exception {
        if (args.length < 5) {
            printUsage();
            return 2;
        }

        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        ScrambleKey key = readKeyArgument(args, 3);

        VideoProcessor.processVideo(input, output, mode, key, null);
        System.out.println((mode == Mode.ENCRYPT ? "Chiffrement" : "Dechiffrement") + " termine : " + output);
        System.out.println("Cle utilisee : " + key);
        return 0;
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
}
