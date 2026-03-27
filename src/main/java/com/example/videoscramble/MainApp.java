package com.example.videoscramble;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

public class MainApp extends Application {
    static {
        OpenCV.loadLocally();
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/example/videoscramble/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 780);
        stage.setTitle("VideoScramble - JavaFX + OpenCV");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
