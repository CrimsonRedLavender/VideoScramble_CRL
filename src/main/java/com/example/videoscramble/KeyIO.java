/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 * Cette classe gere la lecture et l'ecriture des cles dans des fichiers texte.
 */

package com.example.videoscramble;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fonctions pour lire et sauvegarder une cle dans un fichier texte.
 */
public final class KeyIO {
    // pattern pour trouver la cle
    private static final Pattern PATTERN = Pattern.compile("-?\\d+");

    private KeyIO() {
    }

    /**
     * Lit une cle d'un fichier text.
     *
     * @param path chemin du fichier
     * @return cle lue
     * @throws IOException si le fichier ne peut pas etre lu
     */
    public static ScrambleKey read(Path path) throws IOException {
        // Le fichier entier est lu puis confie au parseur commun.
        return parse(Files.readString(path));
    }

    /**
     * Ecrit une cle dans un fichier texte.
     *
     * @param path chemin du fichier a creer ou remplacer
     * @param key cle a ecrire
     * @throws IOException si le fichier ne peut pas etre ecrit
     */
    public static void write(Path path, ScrambleKey key) throws IOException {
        // Format volontairement simple : deux nombres separes par un espace, r puis s.
        Files.writeString(path, key.offset() + " " + key.step());
    }

    /**
     * Extrait la cle du fichier.
     *
     * @param text texte contenant la cle
     * @return cle extraite
     */
    public static ScrambleKey parse(String text) {
        // Le parseur accepte n'importe quel texte contenant au moins deux entiers.
        Matcher matcher = PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Offset introuvable dans la cle.");
        }

        // Premier entier trouve : offset r.
        int offset = Integer.parseInt(matcher.group());

        if (!matcher.find()) {
            throw new IllegalArgumentException("Step introuvable dans la cle.");
        }

        // Deuxieme entier trouve : step s.
        int step = Integer.parseInt(matcher.group());

        // ScrambleKey valide les bornes de r et s.
        return new ScrambleKey(offset, step);
    }
}
