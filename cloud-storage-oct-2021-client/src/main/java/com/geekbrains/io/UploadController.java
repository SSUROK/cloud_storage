package com.geekbrains.io;

import com.geekbrains.network.Net;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javafx.application.Platform.runLater;

@Slf4j
@Setter
public class UploadController {

    public ProgressBar progressBar;
    public Button cancel;
    public Label activeFile;

    public void sendFile(String fileName, Net net, Path rootClient, Path clientFilePath){
        progressBar = new ProgressBar();
        activeFile = new Label();
        activeFile.textProperty().unbind();
        progressBar.progressProperty().unbind();
//        runLater(()->progressBar.setVisible(true));
        ExecutorService executor = Executors.newFixedThreadPool(5);
        SendFile sf = SendFile.builder()
                .fileName(fileName)
                .net(net)
                .rootPath(rootClient)
                .path(clientFilePath)
                .name(String.valueOf(Math.random()))
                .build();
        progressBar.progressProperty().bind(sf.progressProperty());
        activeFile.textProperty().bind(sf.messageProperty());
//        sf.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, //
//                new EventHandler<WorkerStateEvent>() {
//
//                    @Override
//                    public void handle(WorkerStateEvent t) {
//                        fillFilesInServer();
//                    }
//                });
        executor.execute(new Thread(sf, fileName));
    }
}
