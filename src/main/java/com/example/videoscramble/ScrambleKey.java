/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier represente la cle de chiffrement VideoScramble.
 */

package com.example.videoscramble;

/**
 * Cle symetrique composee de l'offset {@code r} et du pas {@code s}.
 *
 * @param offset decalage code sur 8 bits, entre 0 et 255
 * @param step pas code sur 7 bits, entre 0 et 127
 */
public record ScrambleKey(int offset, int step) {
    /**
     * Valide les bornes imposees par le sujet pour les deux composantes.
     */
    public ScrambleKey {
        if (offset < 0 || offset > 255) {
            throw new IllegalArgumentException("offset r doit être entre 0 et 255");
        }
        if (step < 0 || step > 127) {
            throw new IllegalArgumentException("step s doit être entre 0 et 127");
        }
    }

    /**
     * Retourne le coefficient impair {@code 2s + 1} utilise dans la permutation.
     *
     * @return multiplicateur impair associe au pas
     */
    public int oddMultiplier() {
        return 2 * step + 1;
    }

    /**
     * Formate la cle pour l'affichage dans l'interface.
     */
    @Override
    public String toString() {
        return "(r=" + offset + ", s=" + step + ")";
    }
}
