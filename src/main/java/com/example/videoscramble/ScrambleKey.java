/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

/**
 * Cle symetrique composee de l'offset et du pas.
 *
 * @param offset decalage (8 bits)
 * @param step   pas (7 bits)
 */
public record ScrambleKey(int offset, int step) {
    /**
     * Valide les valeurs de l'offset et du step.
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
     * Retourne le coefficient utilise dans la permutation.
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
