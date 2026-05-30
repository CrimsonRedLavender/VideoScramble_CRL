# VideoScramble

Projet Java 17 pour le chiffrement vidéo à l'ancienne :
- JavaFX pour l'interface
- OpenCV pour la lecture/écriture et le traitement vidéo
- chiffrement/déchiffrement par permutation de lignes
- cassure de clé sur une frame extraite d'une vidéo avec score Pearson ou Euclidien

## Bibliothèques utilisées
- JavaFX
- OpenCV (`org.openpnp:opencv`)
- aucune autre bibliothèque métier

## Structure
- `MainApp.java` : lancement JavaFX et chargement OpenCV
- `MainController.java` : interface, boutons et orchestration
- `VideoProcessor.java` : lecture/écriture vidéo OpenCV
- `LineScrambler.java` : permutation des lignes par blocs de puissances de 2
- `ImageCracker.java` : brute force d'une frame extraite de la vidéo
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

Embarquer la clé dans chaque image chiffrée, puis la relire au déchiffrement :
```bash
mvn javafx:run -Djavafx.args="encrypt entree.mp4 sortie.mp4 37 12 --embed-key"
mvn javafx:run -Djavafx.args="decrypt sortie.mp4 video_dechiffree.mp4 --embedded-key"
```

Casser une clé depuis une vidéo chiffrée. Le résultat est une image déchiffrée correspondant à la frame analysée :
```bash
mvn javafx:run -Djavafx.args="crack video_chiffree.mp4 image_dechiffree.png PEARSON"
```

Valider l'algorithme sur une image extraite ou fournie en test, avec un aller-retour chiffrement puis déchiffrement :
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
9. Charger une vidéo chiffrée et utiliser **Casser la clé**

## Remarques techniques
- La permutation s'applique par blocs successifs de tailles puissances de 2.
- L'audio est volontairement ignoré.
- Le fichier de sortie est créé automatiquement si son dossier parent existe ou peut être créé.
- La clé embarquée est encodée dans des blocs clairs/sombres en haut à gauche de l'image. C'est une variante robuste de l'idée du sujet, choisie pour résister au codec `mp4v` disponible sur la machine de démonstration.
- Le code tente aussi d'utiliser FFV1 pour les sorties `.mkv` si le backend OpenCV/FFmpeg local le supporte.
- Le cassage de clé teste toujours les 32768 clés possibles. Pour accélérer la démonstration, la frame est réduite uniquement en largeur avant le calcul du score ; la hauteur reste inchangée pour conserver les mêmes blocs de lignes.
