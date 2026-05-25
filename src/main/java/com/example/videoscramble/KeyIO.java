/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier gere la lecture et l'ecriture des cles dans des fichiers texte.
 */

package com.example.videoscramble;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fonctions utilitaires pour lire et sauvegarder une cle VideoScramble.
 */
public final class KeyIO {
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private KeyIO() {
    }

    /**
     * Lit une cle depuis un fichier texte contenant au moins deux entiers.
     *
     * @param path chemin du fichier contenant {@code r} puis {@code s}
     * @return cle lue dans le fichier
     * @throws IOException si le fichier ne peut pas etre lu
     */
    public static ScrambleKey read(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    /**
     * Ecrit une cle dans un fichier texte au format {@code r s}.
     *
     * @param path chemin du fichier a creer ou remplacer
     * @param key cle a sauvegarder
     * @throws IOException si le fichier ne peut pas etre ecrit
     */
    public static void write(Path path, ScrambleKey key) throws IOException {
        Files.writeString(path, key.offset() + " " + key.step());
    }

    /**
     * Extrait une cle depuis un texte contenant deux entiers.
     *
     * @param text texte contenant {@code r} puis {@code s}
     * @return cle extraite du texte
     */
    public static ScrambleKey parse(String text) {
        Matcher matcher = INTEGER_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Offset r introuvable dans la cle.");
        }
        int offset = Integer.parseInt(matcher.group());

        if (!matcher.find()) {
            throw new IllegalArgumentException("Step s introuvable dans la cle.");
        }
        int step = Integer.parseInt(matcher.group());

        return new ScrambleKey(offset, step);
    }
}
