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

public class MainController {
    @FXML private TextField inputField;
    @FXML private TextField outputField;
    @FXML private TextField offsetField;
    @FXML private TextField stepField;
    @FXML private Label keyLabel;
    @FXML private Label blocksLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<ScoreMethod> scoreCombo;
    @FXML private ImageView sourceView;
    @FXML private ImageView resultView;
    @FXML private CheckBox showPreviewCheckBox;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button crackImageButton;

    @FXML
    public void initialize() {
        scoreCombo.getItems().setAll(ScoreMethod.values());
        scoreCombo.setValue(ScoreMethod.PEARSON);
        offsetField.setText("37");
        stepField.setText("12");
        showPreviewCheckBox.setSelected(true);
        status("Prêt.");
        updateKeyLabel();
    }

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

    @FXML
    private void browseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier de sortie");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MP4", "*.mp4"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        java.io.File file = chooser.showSaveDialog(window());
        if (file != null) {
            outputField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void encrypt() {
        launchVideoTask(Mode.ENCRYPT);
    }

    @FXML
    private void decrypt() {
        launchVideoTask(Mode.DECRYPT);
    }

    @FXML
    private void crackImage() {
        Path input = requiredPath(inputField.getText(), "Choisis l'image chiffrée.");
        Path output = optionalPath(outputField.getText());
        disableActions(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Lecture de l'image...");
                Mat scrambled = Imgcodecs.imread(input.toString());
                if (scrambled.empty()) {
                    throw new IllegalArgumentException("Impossible de lire l'image : " + input);
                }
                Platform.runLater(() -> sourceView.setImage(MatFxUtils.matToImage(scrambled)));

                updateMessage("Brute force en cours...");
                ImageCracker.CrackResult result = ImageCracker.crack(scrambled, scoreCombo.getValue());

                Platform.runLater(() -> {
                    resultView.setImage(MatFxUtils.matToImage(result.image()));
                    keyLabel.setText("Clé trouvée : " + result.key() + " | score = " + String.format("%.6f", result.score()));
                });

                if (output != null) {
                    ensureParent(output);
                    Imgcodecs.imwrite(output.toString(), result.image());
                }
                scrambled.release();
                result.image().release();
                updateMessage("Cassure terminée.");
                return null;
            }
        };
        bindAndRun(task);
    }

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
            Files.writeString(file.toPath(), key.offset() + " " + key.step());
            status("Clé enregistrée : " + file.getAbsolutePath());
        } catch (IOException e) {
            status("Erreur : " + e.getMessage());
        }
    }

    @FXML
    private void updateKeyLabel() {
        try {
            keyLabel.setText("Clé : " + readKey());
        } catch (Exception e) {
            keyLabel.setText("Clé invalide");
        }
    }

    private void launchVideoTask(Mode mode) {
        Path input = requiredPath(inputField.getText(), "Choisis la vidéo d'entrée.");
        Path output = requiredPath(outputField.getText(), "Choisis la vidéo de sortie.");
        ScrambleKey key = readKey();
        disableActions(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement") + " en cours...");
                List<Integer> blocks = VideoProcessor.probeBlocks(input);
                Platform.runLater(() -> blocksLabel.setText("Blocs : " + blocks));

                VideoProcessor.processVideo(input, output, mode, key, showPreviewCheckBox.isSelected() ? (source, result) -> {
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
                } : null);

                updateMessage((mode == Mode.ENCRYPT ? "Chiffrement" : "Déchiffrement") + " terminé.");
                return null;
            }
        };
        bindAndRun(task);
    }

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

    private void finishTask() {
        statusLabel.textProperty().unbind();
        disableActions(false);
    }

    private void disableActions(boolean disabled) {
        encryptButton.setDisable(disabled);
        decryptButton.setDisable(disabled);
        crackImageButton.setDisable(disabled);
    }

    private ScrambleKey readKey() {
        int offset = Integer.parseInt(offsetField.getText().trim());
        int step = Integer.parseInt(stepField.getText().trim());
        return new ScrambleKey(offset, step);
    }

    private Path requiredPath(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return Path.of(text.trim());
    }

    private Path optionalPath(String text) {
        return text == null || text.isBlank() ? null : Path.of(text.trim());
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".mkv");
    }

    private void ensureParent(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private Window window() {
        return inputField.getScene() == null ? null : inputField.getScene().getWindow();
    }

    private void status(String text) {
        statusLabel.textProperty().unbind();
        statusLabel.setText(text);
    }
}
