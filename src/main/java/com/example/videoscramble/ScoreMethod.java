/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier definit les criteres utilises pour casser une cle.
 */

package com.example.videoscramble;

/**
 * Methode de score utilisee pour evaluer la qualite d'une image decryptee.
 */
public enum ScoreMethod {
    /** Correlation de Pearson entre lignes consecutives. */
    PEARSON,

    /** Distance euclidienne entre lignes consecutives. */
    EUCLIDEAN
}
