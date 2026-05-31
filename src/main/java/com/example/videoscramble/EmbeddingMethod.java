/*
 * Projet VideoScramble_CRL
 * Programmation multimedia - JavaFX / OpenCV
 * Ce fichier definit les methodes d'embarquement de cle.
 */

package com.example.videoscramble;

/**
 * Methode utilisee pour stocker une cle dans chaque image chiffree.
 */
public enum EmbeddingMethod {
    /** Methode proche de l'enonce : bits faibles repetes avec vote majoritaire. */
    LSB_MAJORITY,

    /** Methode plus visible mais robuste avec le codec MP4 disponible en demonstration. */
    LUM_BLOCKS
}
