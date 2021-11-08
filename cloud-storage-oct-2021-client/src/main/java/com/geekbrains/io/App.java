package com.geekbrains.io;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class App extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent e) {
                Platform.exit();
                System.exit(0);
            }
        });
        Parent parent = FXMLLoader.load(getClass().getResource("chat-server-client.fxml"));
        primaryStage.setTitle("Cloud");
        primaryStage.setResizable(false);
        primaryStage.setScene(new Scene(parent));
        primaryStage.show();
    }
}
