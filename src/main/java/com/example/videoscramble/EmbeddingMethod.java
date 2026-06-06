/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

/**
 * Methodes possibles pour stocker une cle dans chaque image.
 */
public enum EmbeddingMethod {
    /** Methode utilisants les bits de poid faible et vote majoritaire. */
    LSB_MAJORITY,

    /** Methode blocs pixels sombres/clairs. */
    LUM_BLOCKS
}
