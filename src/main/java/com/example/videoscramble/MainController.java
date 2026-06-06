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
    @FXML private CheckBox changeKeyCheckBox;
    @FXML private TextField keyChangeIntervalField;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button crackImageButton;

    /**
     * Initialise les valeurs par defaut de l'interface.
     */
    @FXML
    public void initialize() {
        offsetField.setText("37");
        stepField.setText("12");
        showPreviewCheckBox.setSelected(true);
        embedKeyCheckBox.setSelected(false);
        readEmbeddedKeyCheckBox.setSelected(false);
        embeddingMethodCombo.getItems().setAll(EmbeddingMethod.values());
        embeddingMethodCombo.setValue(EmbeddingMethod.LUM_BLOCKS);
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
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier d'entrée");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vidéos / images", "*.mp4", "*.avi", "*.mov", "*.mkv", "*.png", "*.jpg", "*.jpeg", "*.bmp"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        java.io.File file = chooser.showOpenDialog(window());
        if (file != null) {
            inputField.setText(file.getAbsolutePath());
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
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier de sortie");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MP4", "*.mp4"),
                new FileChooser.ExtensionFilter("AVI sans perte", "*.avi"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        java.io.File file = chooser.showSaveDialog(window());
        if (file != null) {
            outputField.setText(file.getAbsolutePath());
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

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Lecture de l'image de réféfrence depuis la vidéo...");
                Mat scrambled = readCrackFrame(input);
                Platform.runLater(() -> sourceView.setImage(MatFxUtils.matToImage(scrambled)));

                updateMessage("Brute force en cours...");
                long start = System.nanoTime();
                KeyCracker.CrackResult result = KeyCracker.crack(scrambled);
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

                Platform.runLater(() -> {
                    resultView.setImage(MatFxUtils.matToImage(result.image()));
                    keyLabel.setText("Clé trouvée : " + result.key()
                            + " | score = " + String.format("%.6f", result.score())
                            + " | temps = " + elapsedMillis + " ms");
                });

                if (output != null) {
                    ensureImageOutput(output);
                    ensureParent(output);
                    if (!Imgcodecs.imwrite(output.toString(), result.image())) {
                        throw new IllegalStateException("Impossible d'écrire l'image : " + output);
                    }
                }
                scrambled.release();
                result.image().release();
                updateMessage("Cassage terminé en " + elapsedMillis + " ms.");
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
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Charger la clé");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texte", "*.txt"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            ScrambleKey key = KeyIO.read(file.toPath());
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
        ScrambleKey key = readEmbeddedKeyCheckBox.isSelected() && mode == Mode.DECRYPT ? new ScrambleKey(0, 0) : readKey();
        int keyChangeInterval = changeKeyCheckBox.isSelected() ? readKeyChangeInterval() : 0;
        disableActions(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement") + " en cours...");
                List<Integer> blocks = VideoProcessor.probeBlocks(input);
                Platform.runLater(() -> blocksLabel.setText("Blocs : " + blocks));

                VideoProcessor.processVideo(input, output, mode, key,
                        embedKeyCheckBox.isSelected() && mode == Mode.ENCRYPT,
                        readEmbeddedKeyCheckBox.isSelected() && mode == Mode.DECRYPT,
                        keyChangeInterval,
                        embeddingMethodCombo.getValue(),
                        showPreviewCheckBox.isSelected() ? (source, result) -> {
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
                            sourceCopy.release();
                            resultCopy.release();
                        }
                    });

                    source.release();
                    result.release();
                } : null,
                        usedKey -> Platform.runLater(() -> keyLabel.setText("Clé utilisée : " + usedKey)));

                updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement") + " terminé.");
                return null;
            }
        };
        bindAndRun(task);
    }

    /**
     * Lie une tache sur le statut et la lance.
     */
    private void bindAndRun(Task<Void> task) {
        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> finishTask());
        task.setOnFailed(e -> {
            finishTask();
            Throwable ex = task.getException();
            status("Erreur : " + (ex == null ? "inconnue" : ex.getMessage()));
        });
        Thread thread = new Thread(task, "videoscramble-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Retablit l'interface apres la fin d'une tache.
     */
    private void finishTask() {
        statusLabel.textProperty().unbind();
        disableActions(false);
    }

    /**
     * Active ou desactive les actions pendant un traitement.
     */
    private void disableActions(boolean disabled) {
        encryptButton.setDisable(disabled);
        decryptButton.setDisable(disabled);
        crackImageButton.setDisable(disabled);
    }

    /**
     * Cree une cle a partir des champs de saisie.
     */
    private ScrambleKey readKey() {
        int offset = Integer.parseInt(offsetField.getText().trim());
        int step = Integer.parseInt(stepField.getText().trim());
        return new ScrambleKey(offset, step);
    }

    /**
     * Lit l'intervalle de changement de cle.
     */
    private int readKeyChangeInterval() {
        int interval = Integer.parseInt(keyChangeIntervalField.getText().trim());
        if (interval <= 0) {
            throw new IllegalArgumentException("L'intervalle de changement doit être positif.");
        }
        return interval;
    }

    /**
     * Convertit un texte obligatoire en chemin de fichier.
     */
    private Path requiredPath(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return Path.of(text.trim());
    }

    /**
     * Convertit un texte optionnel en chemin de fichier.
     */
    private Path optionalPath(String text) {
        return text == null || text.isBlank() ? null : Path.of(text.trim());
    }

    /**
     * Determine si le fichier choisi est une video selon son extension.
     */
    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".mkv");
    }

    /**
     * Determine si le fichier choisi est une image selon son extension.
     */
    private boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    /**
     * Verifie que la sortie du cassage est bien une image.
     */
    private void ensureImageOutput(Path output) {
        if (!isImageFile(output)) {
            throw new IllegalArgumentException("La sortie du cassage doit être une image (.png, .jpg, .jpeg ou .bmp) : " + output);
        }
    }

    /**
     * Lit une image ou une frame d'une video.
     */
    private Mat readCrackFrame(Path input) {
        if (isVideoFile(input)) {
            return VideoProcessor.extractRepresentativeFrame(input);
        }

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
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Retourne la fenetre courante pour ouvrir les boites de dialogue.
     */
    private Window window() {
        return inputField.getScene() == null ? null : inputField.getScene().getWindow();
    }

    /**
     * Affiche un message de status dans l'interface.
     */
    private void status(String text) {
        statusLabel.textProperty().unbind();
        statusLabel.setText(text);
    }
}
