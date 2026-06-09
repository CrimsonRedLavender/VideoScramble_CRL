/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 * Cette classe gere la lecture et l'ecriture des cles dans des fichiers texte.
 */

package com.example.videoscramble;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
     * Cle utilisee a partir d'une frame precise.
     *
     * @param frameIndex indice de la premiere frame qui utilise cette cle
     * @param key cle utilisee
     */
    public record KeyFrame(long frameIndex, ScrambleKey key) {}

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
     * Lit une liste de cles changeant pendant une video.
     *
     * @param path fichier texte contenant des lignes frame r s
     * @return liste des cles par frame de debut
     * @throws IOException si le fichier ne peut pas etre lu
     */
    public static List<KeyFrame> readKeyFrames(Path path) throws IOException {
        List<KeyFrame> keyFrames = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            // Les lignes vides et commentaires rendent le fichier plus lisible.
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            Matcher matcher = PATTERN.matcher(trimmed);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Frame introuvable dans la ligne : " + line);
            }
            long frameIndex = Long.parseLong(matcher.group());

            if (!matcher.find()) {
                throw new IllegalArgumentException("Offset introuvable dans la ligne : " + line);
            }
            int offset = Integer.parseInt(matcher.group());

            if (!matcher.find()) {
                throw new IllegalArgumentException("Step introuvable dans la ligne : " + line);
            }
            int step = Integer.parseInt(matcher.group());

            keyFrames.add(new KeyFrame(frameIndex, new ScrambleKey(offset, step)));
        }

        if (keyFrames.isEmpty()) {
            throw new IllegalArgumentException("Aucune cle trouvee dans le fichier : " + path);
        }
        return keyFrames;
    }

    /**
     * Ecrit la liste des cles utilisees pendant une video.
     *
     * @param path fichier a creer ou remplacer
     * @param keyFrames liste des cles par frame de debut
     * @throws IOException si le fichier ne peut pas etre ecrit
     */
    public static void writeKeyFrames(Path path, List<KeyFrame> keyFrames) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# frame offset step").append(System.lineSeparator());
        for (KeyFrame keyFrame : keyFrames) {
            builder.append(keyFrame.frameIndex())
                    .append(' ')
                    .append(keyFrame.key().offset())
                    .append(' ')
                    .append(keyFrame.key().step())
                    .append(System.lineSeparator());
        }
        Files.writeString(path, builder.toString());
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
