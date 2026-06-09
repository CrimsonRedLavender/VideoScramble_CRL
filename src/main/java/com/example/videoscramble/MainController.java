/* Aurélie AZONNOUDO, Cassandre MATHIOT
 * BUT 3 Alternants
 */

package com.example.videoscramble;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Controleur de la fenetre.
 *
 */
public class MainController {
    @FXML private TextField inputField;
    @FXML private TextField outputField;
    @FXML private TextField offsetField;
    @FXML private TextField stepField;
    @FXML private TextField keyLabel;
    @FXML private TextField blocksLabel;
    @FXML private TextField statusLabel;
    @FXML private ImageView sourceView;
    @FXML private ImageView resultView;
    @FXML private CheckBox showPreviewCheckBox;
    @FXML private CheckBox embedKeyCheckBox;
    @FXML private CheckBox readEmbeddedKeyCheckBox;
    @FXML private ComboBox<EmbeddingMethod> embeddingMethodCombo;
    @FXML private ComboBox<ScoreMethod> scoreMethodCombo;
    @FXML private CheckBox changeKeyCheckBox;
    @FXML private TextField keyChangeIntervalField;
    @FXML private TextField keyScheduleField;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button crackImageButton;

    /**
     * Initialise les valeurs par defaut de l'interface.
     */
    @FXML
    public void initialize() {
        // Valeurs de demonstration pour eviter une interface vide au lancement.
        offsetField.setText("37");
        stepField.setText("12");

        // Les options avancees sont desactivees par defaut pour garder le cas simple lisible.
        showPreviewCheckBox.setSelected(true);
        embedKeyCheckBox.setSelected(false);
        readEmbeddedKeyCheckBox.setSelected(false);

        // La methode robuste est choisie par defaut car elle fonctionne avec le codec MP4 de demo.
        embeddingMethodCombo.getItems().setAll(EmbeddingMethod.values());
        embeddingMethodCombo.setValue(EmbeddingMethod.LUM_BLOCKS);
        scoreMethodCombo.getItems().setAll(ScoreMethod.values());
        scoreMethodCombo.setValue(ScoreMethod.EUCLIDEAN);
        changeKeyCheckBox.setSelected(false);
        keyChangeIntervalField.setText("100");
        status("Prêt.");
        updateKeyLabel();
    }

    /**
     * Ouvre un selecteur pour choisir la source.
     */
    @FXML
    private void browseInput() {
        // Le FileChooser limite les formats visibles mais laisse un choix "Tous les fichiers".
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier d'entrée");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vidéos / images", "*.mp4", "*.avi", "*.mov", "*.mkv", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.m4v"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        java.io.File file = chooser.showOpenDialog(window());
        if (file != null) {
            inputField.setText(file.getAbsolutePath());

            // Affiche les blocs immediatement quand l'utilisateur choisit une video.
            if (isVideoFile(file.toPath())) {
                try {
                    List<Integer> blocks = VideoProcessor.probeBlocks(file.toPath());
                    blocksLabel.setText("Blocs : " + blocks);
                } catch (Exception e) {
                    blocksLabel.setText("Blocs : indisponibles");
                }
            }
        }
    }

    /**
     * Ouvre un selecteur pour choisir le fichier de sortie.
     */
    @FXML
    private void browseOutput() {
        // La sortie peut etre une video, ou une image quand on sauvegarde le resultat du crack.
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier de sortie");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MP4", "*.mp4"),
                new FileChooser.ExtensionFilter("AVI sans perte", "*.avi"),
                new FileChooser.ExtensionFilter("M4V", "*.m4v"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        java.io.File file = chooser.showSaveDialog(window());
        if (file != null) {
            outputField.setText(file.getAbsolutePath());
        }
    }

    /**
     * Ouvre un selecteur pour choisir le fichier keys.txt utilise au dechiffrement.
     */
    @FXML
    private void browseKeySchedule() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Charger la liste des clés");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file != null) {
            keyScheduleField.setText(file.getAbsolutePath());
        }
    }

    /**
     * Lance le chiffrement de la video.
     */
    @FXML
    private void encrypt() {
        launchVideoTask(Mode.ENCRYPT);
    }

    /**
     * Lance le dechiffrement de la video.
     */
    @FXML
    private void decrypt() {
        launchVideoTask(Mode.DECRYPT);
    }

    /**
     * Cherche la cle par brute force.
     */
    @FXML
    private void keyCracker() {
        Path input = requiredPath(inputField.getText(), "Choisis la vidéo chiffree.");
        Path output = optionalPath(outputField.getText());
        disableActions(true);

        // Le brute force est lance dans une Task pour ne pas bloquer le thread JavaFX.
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Lecture de l'image de réféfrence depuis la vidéo...");
                Mat scrambled = readCrackFrame(input);

                // Les changements d'IHM doivent toujours passer par le thread JavaFX.
                Platform.runLater(() -> sourceView.setImage(MatFxUtils.matToImage(scrambled)));

                updateMessage("Brute force en cours...");
                long start = System.nanoTime();
                KeyCracker.CrackResult result = KeyCracker.crack(scrambled, scoreMethodCombo.getValue());
                double elapsedSeconds = secondsSince(start);

                // Affiche l'image reconstruite et les informations utiles pour la demonstration.
                Platform.runLater(() -> {
                    resultView.setImage(MatFxUtils.matToImage(result.image()));
                    keyLabel.setText("Clé trouvée : " + result.key()
                            + " | score = " + String.format("%.6f", result.score())
                            + " | temps = " + formatSeconds(elapsedSeconds) + " s");
                });

                if (output != null) {
                    // Le crack produit une image fixe, pas une video.
                    ensureImageOutput(output);
                    ensureParent(output);
                    if (!Imgcodecs.imwrite(output.toString(), result.image())) {
                        throw new IllegalStateException("Impossible d'écrire l'image : " + output);
                    }
                }

                // Liberation manuelle des Mat OpenCV creees pendant le traitement.
                scrambled.release();
                result.image().release();
                updateMessage("Cassage terminé en " + formatSeconds(elapsedSeconds) + " s.");
                return null;
            }
        };
        bindAndRun(task);
    }

    /**
     * Enregistre la cle dans un fichier texte.
     */
    @FXML
    private void saveKeyToText() {
        ScrambleKey key = readKey();

        // La cle est stockee dans un fichier texte simple pour pouvoir etre partagee/rechargee.
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer la clé");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
        java.io.File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        try {
            KeyIO.write(file.toPath(), key);
            status("Clé enregistrée : " + file.getAbsolutePath());
        } catch (IOException e) {
            status("Erreur : " + e.getMessage());
        }
    }

    /**
     * Charge une cle d'un fichier texte.
     */
    @FXML
    private void loadKeyFromText() {
        // Charge un fichier contenant au moins deux entiers : r puis s.
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Charger la clé");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            ScrambleKey key = KeyIO.read(file.toPath());

            // Les champs sont mis a jour pour que l'utilisateur voie la cle chargee.
            offsetField.setText(Integer.toString(key.offset()));
            stepField.setText(Integer.toString(key.step()));
            updateKeyLabel();
            status("Clé chargée : " + file.getAbsolutePath());
        } catch (IOException | IllegalArgumentException e) {
            status("Erreur : " + e.getMessage());
        }
    }

    /**
     * Met a jour l'affichage de la cle quand les champs changent.
     */
    @FXML
    private void updateKeyLabel() {
        try {
            // readKey valide aussi les bornes de r et s via ScrambleKey.
            keyLabel.setText("Clé : " + readKey());
        } catch (Exception e) {
            keyLabel.setText("Clé invalide");
        }
    }

    /**
     * Lance une tache de traitement video.
     */
    private void launchVideoTask(Mode mode) {
        Path input = requiredPath(inputField.getText(), "Choisis la vidéo d'entrée.");
        Path output = requiredPath(outputField.getText(), "Choisis la vidéo de sortie.");

        // Si on lit une cle embarquee, la cle saisie n'est qu'une valeur neutre non utilisee.
        boolean useEmbeddedKey = readEmbeddedKeyCheckBox.isSelected() && mode == Mode.DECRYPT;
        boolean useKeySchedule = mode == Mode.DECRYPT && changeKeyCheckBox.isSelected() && !useEmbeddedKey;
        ScrambleKey key = useEmbeddedKey || useKeySchedule ? new ScrambleKey(0, 0) : readKey();
        int keyChangeInterval = changeKeyCheckBox.isSelected() ? readKeyChangeInterval() : 0;
        Path keySchedulePath = useKeySchedule
                ? requiredPath(keyScheduleField.getText(), "Choisis le fichier keys.txt utilise au chiffrement.")
                : null;
        disableActions(true);

        // Traitement potentiellement long : execution en arriere-plan.
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long start = System.nanoTime();
                updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement") + " en cours...");
                List<Integer> blocks = VideoProcessor.probeBlocks(input);
                Platform.runLater(() -> blocksLabel.setText("Blocs : " + blocks));
                List<KeyIO.KeyFrame> keySchedule = keySchedulePath == null ? null : KeyIO.readKeyFrames(keySchedulePath);

                // VideoProcessor gere la boucle frame par frame ; le controleur fournit les options IHM.
                List<KeyIO.KeyFrame> usedKeys = VideoProcessor.processVideo(input, output, mode, key,
                        embedKeyCheckBox.isSelected() && mode == Mode.ENCRYPT,
                        readEmbeddedKeyCheckBox.isSelected() && mode == Mode.DECRYPT,
                        keyChangeInterval,
                        keySchedule,
                        embeddingMethodCombo.getValue(),
                        showPreviewCheckBox.isSelected() ? (source, result) -> {
                    // Les Mat sont clonees pour survivre jusqu'a l'execution du runLater.
                    Mat sourceCopy = source.clone();
                    Mat resultCopy = result.clone();

                    Platform.runLater(() -> {
                        try {
                            var left = MatFxUtils.matToImage(sourceCopy);
                            var right = MatFxUtils.matToImage(resultCopy);

                            if (left != null) {
                                sourceView.setImage(left);
                            }
                            if (right != null) {
                                resultView.setImage(right);
                            }
                        } finally {
                            // Les clones d'affichage doivent aussi etre liberes.
                            sourceCopy.release();
                            resultCopy.release();
                        }
                    });

                    source.release();
                    result.release();
                } : null,
                        // Affiche la cle courante, utile quand elle change ou quand elle est embarquee.
                        usedKey -> Platform.runLater(() -> keyLabel.setText("Clé utilisée : " + usedKey)));

                double elapsedSeconds = secondsSince(start);

                if (mode == Mode.ENCRYPT && keyChangeInterval > 0) {
                    Path keysPath = defaultKeysPath(output);
                    KeyIO.writeKeyFrames(keysPath, usedKeys);
                    Platform.runLater(() -> keyScheduleField.setText(keysPath.toString()));
                    updateMessage("Chiffrement terminé en " + formatSeconds(elapsedSeconds)
                            + " s. Liste des clés enregistrée : " + keysPath);
                } else {
                    updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement")
                            + " terminé en " + formatSeconds(elapsedSeconds) + " s.");
                }
                return null;
            }
        };
        bindAndRun(task);
    }

    /**
     * Lie une tache sur le statut et la lance.
     */
    private void bindAndRun(Task<Void> task) {
        // Le message de la Task devient automatiquement le texte du statut.
        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> finishTask());
        task.setOnFailed(e -> {
            // En cas d'erreur, on reactive l'IHM puis on affiche le message lisible.
            finishTask();
            Throwable ex = task.getException();
            status("Erreur : " + (ex == null ? "inconnue" : ex.getMessage()));
        });
        Thread thread = new Thread(task, "videoscramble-task");
        // Le thread daemon n'empeche pas la fermeture de l'application.
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Retablit l'interface apres la fin d'une tache.
     */
    private void finishTask() {
        // On detache le binding pour pouvoir reecrire manuellement dans status().
        statusLabel.textProperty().unbind();
        disableActions(false);
    }

    /**
     * Active ou desactive les actions pendant un traitement.
     */
    private void disableActions(boolean disabled) {
        // Evite de lancer deux traitements video/brute force en meme temps.
        encryptButton.setDisable(disabled);
        decryptButton.setDisable(disabled);
        crackImageButton.setDisable(disabled);
    }

    /**
     * Cree une cle a partir des champs de saisie.
     */
    private ScrambleKey readKey() {
        // Les champs texte sont convertis en entiers puis valides par ScrambleKey.
        int offset = Integer.parseInt(offsetField.getText().trim());
        int step = Integer.parseInt(stepField.getText().trim());
        return new ScrambleKey(offset, step);
    }

    /**
     * Lit l'intervalle de changement de cle.
     */
    private int readKeyChangeInterval() {
        // L'intervalle indique combien d'images gardent la meme cle avant changement.
        int interval = Integer.parseInt(keyChangeIntervalField.getText().trim());
        if (interval <= 0) {
            throw new IllegalArgumentException("L'intervalle de changement doit être positif.");
        }
        return interval;
    }

    /**
     * Calcule une duree en secondes depuis un temps de depart nanoTime.
     */
    private double secondsSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    /**
     * Formate une duree en secondes pour l'affichage.
     */
    private String formatSeconds(double seconds) {
        return String.format("%.3f", seconds);
    }

    /**
     * Retourne le fichier keys.txt cree automatiquement a cote de la video chiffree.
     */
    private Path defaultKeysPath(Path output) {
        Path parent = output.toAbsolutePath().getParent();
        return (parent == null ? Path.of(".") : parent).resolve("keys.txt");
    }

    /**
     * Convertit un texte obligatoire en chemin de fichier.
     */
    private Path requiredPath(String text, String message) {
        // Utilise pour les chemins indispensables : entree video et sortie video.
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return Path.of(text.trim());
    }

    /**
     * Convertit un texte optionnel en chemin de fichier.
     */
    private Path optionalPath(String text) {
        // Utilise pour la sortie image du crack, qui peut etre omise.
        return text == null || text.isBlank() ? null : Path.of(text.trim());
    }

    /**
     * Determine si le fichier choisi est une video selon son extension.
     */
    private boolean isVideoFile(Path path) {
        // Detection volontairement basee sur l'extension pour rester simple.
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".mkv");
    }

    /**
     * Determine si le fichier choisi est une image selon son extension.
     */
    private boolean isImageFile(Path path) {
        // OpenCV imwrite a besoin d'une extension image supportee.
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    /**
     * Verifie que la sortie du cassage est bien une image.
     */
    private void ensureImageOutput(Path output) {
        // Evite l'erreur obscure d'OpenCV si l'utilisateur met .mp4 en sortie de crack.
        if (!isImageFile(output)) {
            throw new IllegalArgumentException("La sortie du cassage doit être une image (.png, .jpg, .jpeg ou .bmp) : " + output);
        }
    }

    /**
     * Lit une image ou une frame d'une video.
     */
    private Mat readCrackFrame(Path input) {
        // Dans le sujet, on casse une frame ; si l'entree est une video, on l'extrait.
        if (isVideoFile(input)) {
            return VideoProcessor.extractRepresentativeFrame(input);
        }

        // Garde la possibilite de tester le crack directement sur une image sauvegardee.
        Mat image = Imgcodecs.imread(input.toString());
        if (image.empty()) {
            throw new IllegalArgumentException("Impossible de lire l'image : " + input);
        }
        return image;
    }

    /**
     * Cree le dossier parent d'un fichier si celui-ci n'existe pas.
     */
    private void ensureParent(Path path) throws IOException {
        // Cree le dossier de sortie si l'utilisateur indique un chemin encore inexistant.
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Retourne la fenetre courante pour ouvrir les boites de dialogue.
     */
    private Window window() {
        // Certains tests peuvent appeler le controleur avant que la scene soit attachee.
        return inputField.getScene() == null ? null : inputField.getScene().getWindow();
    }

    /**
     * Affiche un message de status dans l'interface.
     */
    private void status(String text) {
        // Unbind obligatoire si le statut etait lie a une Task terminee.
        statusLabel.textProperty().unbind();
        statusLabel.setText(text);
    }
}
