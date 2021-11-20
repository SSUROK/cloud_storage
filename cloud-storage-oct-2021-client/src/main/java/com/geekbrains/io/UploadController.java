package com.geekbrains.io;

import com.geekbrains.model.FileDelete;
import com.geekbrains.model.FileMessage;
import com.geekbrains.network.Net;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Setter
public class UploadController implements Initializable {

    public AnchorPane anchorPane;
    public ProgressBar progressBar;
    public Button cancel;
    public Label activeFile;
    public Button okButton;

    private static byte[] buffer;
    private Path path;
    private Path rootPath;
    private long progress = 0;
    private static Task task;
    private static Net net;
    private String fileName;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buffer = new byte[65536];
        okButton.setVisible(false);
    }

    public void sendFile(String fileName, Net net, Path rootClient, Path clientFilePath, Stage stage) throws Exception{
        this.net = net;
        this.fileName = fileName;
//        activeFile = new Label();
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent e) {
                if(task.isRunning()){
                    task.cancel();
                    net.send(new FileDelete(fileName));
                }
                stage.close();
            }
        });

        rootPath = rootClient;
        path = clientFilePath;
        progressBar.progressProperty().unbind();
        activeFile.textProperty().unbind();
        task = new Task() {
            @Override
            protected Object call() throws Exception {
                path = Paths.get(path.resolve(fileName).toString());
                List<Path> paths = new ArrayList<>();

                if (Files.isDirectory(path)) {
                    paths = directoryParsing(path);
                } else {
                    paths.add(path);
                }
                paths.forEach(p -> {
                    if (Files.exists(p)) {
                        String path;
                        if (!p.getParent().equals(rootPath)) {
                            path = p.getParent().toString();
                            path = path.replace(rootPath.toString() + "/", "");
                        } else {
                            path = "";
                        }
                        this.updateMessage(path + fileName);
                        boolean isFirstButch = true;
                        long size = 0;
                        try {
                            size = Files.size(p);
                        } catch (IOException e) {
                            log.error("e:", e);
                        }
                        log.info("File {}", p);
                        long time = size/1000000000*2;
                        try (FileInputStream fis = new FileInputStream(p.toFile())) {
                            int read;
                            while ((read = fis.read(buffer)) != -1) {
                                progress +=read;
                                FileMessage message = FileMessage.builder()
                                        .bytes(buffer)
                                        .name(p.getFileName().toString())
                                        .path(path)
                                        .size(size)
                                        .isFirstButch(isFirstButch)
                                        .isFinishBatch(fis.available() <= 0)
                                        .endByteNum(read)
                                        .build();
                                this.updateProgress(progress, size);
                                net.send(message);
                                isFirstButch = false;
                                TimeUnit.NANOSECONDS.sleep(1);
                            }
                            progress = 0;
                        } catch (Exception e) {
                            log.error("e:", e);
                        }
                    }
                });
                return null;
            }
        };

        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, //
                new EventHandler<WorkerStateEvent>() {

                    @Override
                    public void handle(WorkerStateEvent t) {
                        okButton.setVisible(true);
                        cancel.setVisible(false);
                        activeFile.textProperty().unbind();
                        activeFile.setText("Completed");
                    }
                });

        progressBar.progressProperty().bind(task.progressProperty());
        activeFile.textProperty().bind(task.messageProperty());

        new Thread(task, fileName).start();
    }

    private List<Path> directoryParsing(Path fp){
        List<Path> paths = new ArrayList<>();
        File[] files = fp.toFile().listFiles();
        for (File f : files){
            if (f.isDirectory()){
                paths.addAll(directoryParsing(f.toPath()));
            } else {
                paths.add(f.toPath());
            }
        }
        return paths;
    }

    public void cancel(ActionEvent event) {
        task.cancel();
        net.send(new FileDelete(fileName));
        okButton.setVisible(true);
    }

    public void accept(ActionEvent event) {
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}