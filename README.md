# VideoScramble - version finale propre pour soutenance

Projet Java 17 conforme au cahier des charges :
- JavaFX pour l'interface
- OpenCV pour la lecture/écriture et le traitement vidéo
- chiffrement/déchiffrement par permutation de lignes
- cassure de clé sur image par force brute avec score Pearson ou Euclidien

## Bibliothèques utilisées
- JavaFX
- OpenCV (`org.openpnp:opencv`)
- aucune autre bibliothèque métier

## Structure
- `MainApp.java` : lancement JavaFX et chargement OpenCV
- `MainController.java` : interface, boutons et orchestration
- `VideoProcessor.java` : lecture/écriture vidéo OpenCV
- `LineScrambler.java` : permutation des lignes par blocs de puissances de 2
- `ImageCracker.java` : brute force d'une image
- `CommandLineRunner.java` : mode ligne de commande
- `KeyIO.java` : lecture/écriture d'une clé dans un fichier texte

## Prérequis
- JDK 17
- Maven

## Lancer le projet
```bash
mvn clean javafx:run
```

## Mode ligne de commande
Le programme peut aussi traiter une vidéo sans ouvrir l'interface graphique.

Chiffrer avec une clé saisie directement :
```bash
mvn javafx:run -Djavafx.args="encrypt entree.mp4 sortie.mp4 37 12"
```

Déchiffrer avec une clé saisie directement :
```bash
mvn javafx:run -Djavafx.args="decrypt entree.mp4 sortie.mp4 37 12"
```

Utiliser une clé stockée dans un fichier texte :
```bash
mvn javafx:run -Djavafx.args="encrypt entree.mp4 sortie.mp4 --key-file cle.txt"
mvn javafx:run -Djavafx.args="decrypt entree.mp4 sortie.mp4 --key-file cle.txt"
```

Valider l'algorithme sur une image avec un aller-retour chiffrement puis déchiffrement :
```bash
mvn javafx:run -Djavafx.args="validate-image image.png 37 12"
```

Le fichier de clé contient simplement deux entiers :
```text
37 12
```

## Démonstration conseillée en soutenance
1. Charger une vidéo
2. Choisir un fichier de sortie
3. Saisir une clé `(r, s)`
4. Cliquer sur **Chiffrer la vidéo**
5. Recharger la vidéo chiffrée en entrée
6. Reprendre la même clé
7. Cliquer sur **Déchiffrer la vidéo**
8. Montrer que l'image d'origine est retrouvée
9. Charger une image chiffrée et utiliser **Casser une image**

## Remarques techniques
- La permutation s'applique par blocs successifs de tailles puissances de 2.
- L'audio est volontairement ignoré.
- Le fichier de sortie est créé automatiquement si son dossier parent existe ou peut être créé.
