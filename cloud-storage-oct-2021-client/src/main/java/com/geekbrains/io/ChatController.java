package com.geekbrains.io;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


import com.geekbrains.model.*;
import com.geekbrains.network.Net;
import io.netty.channel.ChannelHandlerContext;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static javafx.application.Platform.runLater;

@Slf4j
@Setter
public class ChatController implements Initializable {

    public ListView<String> listLeft;
    public ListView<String> listRight;
    public TextField input;
    public Button upload;
    public Button download;
    public Button reconnect;
    public ContextMenu contextMenuLeft;
    public ContextMenu contextMenuRight;
    public ProgressBar progressBar;
    private Path rootClient;
    private Path rootServer;
    private Path clientFilePath;
    private Path serverFilePath;
    private byte[] buffer;
    private Net net;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        buffer = new byte[8192];
        rootClient = Paths.get("root-client");
        rootServer = Paths.get("root-server");
        clientFilePath = rootClient;
        serverFilePath = rootServer;
        if (!Files.exists(rootClient)) {
            try {
                Files.createDirectory(rootClient);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        try {
            fillFilesInClient();
        } catch (Exception e) {
            e.printStackTrace();
        }

        progressBar.setVisible(false);

        listRight.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                String fileName = listRight.getSelectionModel().getSelectedItem();
                if (fileName.equals("Back to previous")){
                    if (serverFilePath.getParent() != null) {
                        serverFilePath = serverFilePath.getParent();
                        try {
                            net.send(new ListRequest(".."));
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    } else {
                        input.setText("no");
                    }
                } else if (Files.isDirectory(serverFilePath.resolve(fileName))) {
                    serverFilePath = Paths.get(serverFilePath.resolve(fileName).toString());
                    try {
                        net.send(new ListRequest(listRight.getSelectionModel().getSelectedItem()));
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else if (!Files.isDirectory(serverFilePath.resolve(fileName))){
                    download.fire();
                } else {
                    input.setText("Select file! Not directory");
                }
            }
        });

        // настройка действий списка файлов клиента
        listLeft.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                String fileName = listLeft.getSelectionModel().getSelectedItem();
                if (fileName.equals("Back to previous")){
                    if (clientFilePath.getParent() != null) {
                        clientFilePath = clientFilePath.getParent();
                        try {
                            fillFilesInClient();
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    } else {
                        input.setText("no");
                    }
                } else if (Files.isDirectory(clientFilePath.resolve(fileName))) {
                    clientFilePath = Paths.get(clientFilePath.resolve(fileName).toString());
                    try {
                        fillFilesInClient();
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else if (!Files.isDirectory(clientFilePath.resolve(fileName))){
                    upload.fire();
                } else {
                    input.setText("Select file! Not directory");
                }
            }
        });

        net = Net.getInstance(this::processMessage); // wait
        log.info(String.valueOf(Thread.activeCount()));
    }

    public void reconnect(ActionEvent e){
        if (!net.isAlive()) {
            log.debug("Trying to reconnect");
            net.kill();
            net = Net.getInstance(this::processMessage);
        }
    }

    private void processMessage(AbstractMessage message) throws Exception {
//        log.debug("Start processing {}", message);
        switch (message.getType()) {

            case FILE_MESSAGE:
                FileMessage msg = (FileMessage) message;
                clientFilePath = rootClient.resolve(msg.getPath());
                if (!Files.exists(clientFilePath)) {
                    Files.createDirectory(clientFilePath);
                }
                log.info("File path {}", clientFilePath);
                Path file = clientFilePath.resolve(msg.getName());

                if (msg.isFirstButch()) {
                    Files.deleteIfExists(file);
                }

                try (FileOutputStream os = new FileOutputStream(file.toFile(), true)) {
                    os.write(msg.getBytes(), 0, msg.getEndByteNum());
                }

                fillFilesInClient();
                break;
            case LIST_MESSAGE:
                ListMessage list = (ListMessage) message;
                runLater(() -> {
                    listRight.getItems().clear();
                    listRight.getItems().add("Back to previous");
                    listRight.getItems().addAll(list.getFiles());
                });
                break;
            case SERVER_OFFLINE:
                runLater(()-> {
                    listRight.getItems().clear();
                    input.setText("Server offline");
                });
                break;
            case SERVER_ONLINE:
                log.info("connected");
                runLater(()->{
                    input.setText("Connected");
                });
                break;
        }
    }

    private void fillFilesInClient() {
        runLater(()->{
            listLeft.getItems().clear();
            listLeft.getItems().add("Back to previous");
            List<String> list = null;
            try {
                list = Files.list(clientFilePath)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
            }
            listLeft.getItems().addAll(list);
        });
    }

    private void fillFilesInServer(){
        try {
            net.send(new ListRequest(serverFilePath.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*отправка файла серверу*/
    public void sendFile(ActionEvent actionEvent) throws IOException{
        String fileName = listLeft.getSelectionModel().getSelectedItem();
        progressBar.progressProperty().unbind();
        runLater(()->progressBar.setVisible(true));
        ExecutorService executor = Executors.newFixedThreadPool(5);
        SendFile sf = SendFile.builder()
                .name(fileName)
                .net(net)
                .rootPath(rootClient)
                .path(clientFilePath)
                .progressBar(progressBar)
                .build();
        sf.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, //
                new EventHandler<WorkerStateEvent>() {

                    @Override
                    public void handle(WorkerStateEvent t) {
                        fillFilesInServer();
                    }
                });
        executor.execute(new Thread(sf, fileName));
        progressBar.setProgress(0);
        runLater(()-> progressBar.setVisible(false));
//        clientFilePath = Paths.get(clientFilePath.resolve(fileName).toString());
//        runLater(()->progressBar.setVisible(true));
//        List<Path> paths = new ArrayList<>();
//        if (Files.isDirectory(clientFilePath)) {
//            paths = directoryParsing(clientFilePath);
//        } else {
//            paths.add(clientFilePath);
//        }
//        paths.forEach(p -> {
//            if (Files.exists(p)) {
//                String path;
//                if (!p.getParent().equals(rootClient)) {
//                    path = p.getParent().toString();
//                    path = path.replace(rootClient.toString() + "/", "");
//                } else {
//                    path = "";
//                }
//                boolean isFirstButch = true;
//                long size = 0;
//                try {
//                    size = Files.size(p);
//                    System.out.println("size " + size);
//                } catch (IOException e) {
//                    log.error("e:", e);
//                }
//                try (FileInputStream fis = new FileInputStream(p.toFile())) {
//                    int read;
//                    while ((read = fis.read(buffer)) != -1) {
////                        log.info(fileName, path, size);
//                        FileMessage message = FileMessage.builder()
//                                .bytes(buffer)
//                                .name(p.getFileName().toString())
//                                .path(path)
//                                .size(size)
//                                .isFirstButch(isFirstButch)
//                                .isFinishBatch(fis.available() <= 0)
//                                .endByteNum(read)
//                                .build();
//                        net.send(message);
//                        int r = read;
////                        System.out.println(read);
////                        runLater(()->{
////                            progressBar.setProgress((double) r/buffer.length);
////                        });
//                        isFirstButch = false;
//                    }
//                } catch (Exception e) {
//                    log.error("e:", e);
//                }
//            }
//        });
//        progressBar.setVisible(false);
//        clientFilePath = clientFilePath.getParent();
    }

    public void requestFile(ActionEvent event){
        String name = listRight.getSelectionModel().getSelectedItem();
        serverFilePath = serverFilePath.resolve(name);
        net.send(new FileRequest(serverFilePath.toString()));
        serverFilePath = serverFilePath.getParent();
    }

    /*представление директории в виде конченых файлов с путями*/
    private List<Path> directoryParsing(Path fp){
        List<Path> paths = new ArrayList<>();
        File[] files = fp.toFile().listFiles();
//        System.out.println(Arrays.toString(files));
        for (File f : files){
            if (f.isDirectory()){
                paths.addAll(directoryParsing(f.toPath()));
            } else {
                paths.add(f.toPath());
            }
        }
        return paths;
    }

    public void newFolderServer(ActionEvent event) {
        FileMessage folder = FileMessage.builder()
                .path(input.getText())
                .isFirstButch(true)
                .build();
        net.send(folder);
        fillFilesInServer();
    }

    public void refreshServer(ActionEvent event) {
        fillFilesInServer();
    }

    public void deleteServer(ActionEvent event) {
        net.send(new FileDelete(serverFilePath.resolve(listRight.getSelectionModel().getSelectedItem()).toString()));
        fillFilesInServer();
    }

    public void newFolderClient(ActionEvent event) {
        fillFilesInClient();
    }

    public void refreshClient(ActionEvent event) {
        String name = input.getText();
        Path nf = clientFilePath.resolve(name);
        if(!Files.exists(nf)){
            try {
                Files.createDirectory(nf);
            } catch (IOException e) {
                e.printStackTrace();
            }
            fillFilesInClient();
        }
    }

    public void deleteClient(ActionEvent event) {
        String name = listLeft.getSelectionModel().getSelectedItem();
        try {
            Files.delete(clientFilePath.resolve(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
        fillFilesInClient();
    }
}
