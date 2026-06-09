/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

/**
 * Methode de score utilisee pour evaluer une cle candidate pendant le cassage.
 */
public enum ScoreMethod {
    /** Distance euclidienne entre lignes consecutives. */
    EUCLIDEAN,

    /** Correlation de Pearson entre lignes consecutives. */
    PEARSON
}
