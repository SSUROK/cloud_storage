package com.geekbrains.io;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;


import com.geekbrains.classes.Command;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class ChatController implements Initializable {

    public ListView<String> listLeft;
    public ListView<String> listRight;
    public TextField input;
    public Button upload;
    public Button download;
    private Path rootClient;
    private Path rootServer;
    private Path clientFilePath;
    private Path serverFilePath;
    private byte[] buffer;
    private DataInputStream dis;
    private DataOutputStream dos;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buffer = new byte[1024];
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

        // настройка действий списка файлов клиента
        listLeft.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
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

        //подключение к серверу
        try {
            Socket socket = new Socket("localhost", 8189);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            Thread readThread = new Thread(() -> {
                try {
                    while (true) {
                        String message = dis.readUTF();
                        Platform.runLater(() -> input.setText(message));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            readThread.setDaemon(true);
            readThread.start();
            fillFilesInServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*запрос и дальнейшее заполнение списка файлов сервера*/
    private void fillFilesInServer() throws IOException{
        listRight.getItems().clear();
        listRight.getItems().add("Back to previous");
        Command com = new Command(Command.LIST);
        sendCommand(com);
    }

    private void fillFilesInClient() throws Exception {
        listLeft.getItems().clear();
        listLeft.getItems().add("Back to previous");
        List<String> list = Files.list(clientFilePath)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        listLeft.getItems().addAll(list);
    }

    /*отправка команды серверу*/
    private void sendCommand(Command command) throws IOException {
//        System.out.println(Arrays.toString(command.getByteArray()));
        byte[] com = command.getByteArray();
        dos.writeLong(com.length);
        for(byte b : com){
            dos.writeByte(b);
        }
    }

    /*отправка файла серверу*/
    public void sendMessage(ActionEvent actionEvent) throws IOException {
        dos.write("file".getBytes(StandardCharsets.UTF_8));
        String fileName = listLeft.getSelectionModel().getSelectedItem();
        clientFilePath = Paths.get(clientFilePath.resolve(fileName).toString());
        List<Path> paths = new ArrayList<>();
        if (Files.isDirectory(clientFilePath)) {
            paths = directoryParsing(clientFilePath);
//            paths.forEach(System.out::println);
//            input.setText("choose file. not directory");
        } else {
            paths.add(clientFilePath);
        }
//        Path filePath = root.resolve(fileName);
        paths.forEach(p -> {
            try {
                if (Files.exists(p)) {
                    String path;
                    if (!p.getParent().equals(rootClient)) {
                        path = p.getParent().toString();
                        System.out.println(path);
                        path = path.replace(rootClient.toString() + "/", "");
                    } else {
                        path = "";
                    }
                    dos.writeUTF(p.getFileName().toString());
                    dos.writeUTF(path);
                    dos.writeLong(Files.size(p));
                    try (FileInputStream fis = new FileInputStream(p.toFile())) {
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, read);
                        }
                    }
                    dos.flush();
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        });
        clientFilePath = clientFilePath.getParent();
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
}
