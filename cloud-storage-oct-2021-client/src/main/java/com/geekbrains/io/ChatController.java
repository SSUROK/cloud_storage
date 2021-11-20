package com.geekbrains.io;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.geekbrains.model.*;
import com.geekbrains.network.Net;
import io.netty.channel.ChannelHandlerContext;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
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
    private Path rootClient;
    private Path rootServer;
    private Path clientFilePath;
    private Path serverFilePath;
    private Net net;
    private Parent parent;
    private UploadController uploadController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        rootClient = Paths.get("root-client");
        rootServer = Paths.get("root-server");
        clientFilePath = rootClient;
        serverFilePath = rootServer;
        net = Net.getInstance(this::processMessage);
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

        listRight.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                String fileName = listRight.getSelectionModel().getSelectedItem();
                if (fileName.equals("Back to previous")){
                    if (serverFilePath.getParent() != null) {
                        serverFilePath = serverFilePath.getParent();
                        net.send(new ListRequest(".."));
                    } else {
                        input.setText("no");
                    }
                } else if (!fileName.contains(".")) {
                    serverFilePath = Paths.get(serverFilePath.resolve(fileName).toString());
                    net.send(new ListRequest(fileName));
                } else {
                    download.fire();
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
                processFile((FileMessage) message);
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

    private void processFile(FileMessage msg) throws Exception {
        /**
         * метод получения файла такой же как и у сервера, у сервера файлы приходят отлично, здесь контроллер на половине решает перестать получать сообщения
         */
        String fileName = msg.getName();
        Path path = Paths.get(msg.getPath());
        clientFilePath = rootClient.resolve(path);
        if (!Files.exists(clientFilePath)) {
            Files.createDirectory(clientFilePath);
        }
        Path file = clientFilePath.resolve(fileName);
        clientFilePath = rootClient;

        if (msg.isFirstButch()) {
            Files.deleteIfExists(file);
        }

        try(FileOutputStream os = new FileOutputStream(file.toFile(), true)) {
            os.write(msg.getBytes(), 0, msg.getEndByteNum());
        }

        if (msg.isFinishBatch()) {
            fillFilesInClient();
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
        net.send(new ListRequest(serverFilePath.toString()));
    }

    /*отправка файла серверу*/
    public void sendFile(ActionEvent actionEvent) throws Exception{
        Stage stage = new Stage();
        String fileName = listLeft.getSelectionModel().getSelectedItem();
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("upload-screen.fxml"));
        parent = loader.load();
        stage.setTitle("Upload");
        stage.setResizable(false);
        stage.setScene(new Scene(parent));
        stage.show();
        uploadController = loader.getController();
        uploadController.sendFile(fileName, net, rootClient, clientFilePath, stage);
    }

    public void requestFile(ActionEvent event){
        String name = listRight.getSelectionModel().getSelectedItem();
        serverFilePath = serverFilePath.resolve(name);
        net.send(new FileRequest(serverFilePath.toString()));
        serverFilePath = serverFilePath.getParent();
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
        net.send(new FileDelete((listRight.getSelectionModel().getSelectedItem())));
        fillFilesInServer();
    }

    public void newFolderClient(ActionEvent event) {
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

    public void refreshClient(ActionEvent event) {
        fillFilesInClient();
    }

    public void deleteClient(ActionEvent event) {
        String name = listLeft.getSelectionModel().getSelectedItem();
        deleteFile(clientFilePath.resolve(name));
    }

    private void deleteFile(Path path){
        try {
            if(Files.isDirectory(path)) {
                List<File> contains = Arrays.asList(path.toFile().listFiles());
                if (!contains.isEmpty()) {
                    contains.forEach(e -> {
                        deleteFile(e.toPath());
                    });
                }
            }
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        fillFilesInClient();
    }
}
