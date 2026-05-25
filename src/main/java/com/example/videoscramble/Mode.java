/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier definit les modes de traitement video.
 */

package com.example.videoscramble;

/**
 * Mode de traitement applique a une video.
 */
public enum Mode {
    /** Chiffrement par permutation directe des lignes. */
    ENCRYPT,

    /** Dechiffrement par permutation inverse des lignes. */
    DECRYPT
}
