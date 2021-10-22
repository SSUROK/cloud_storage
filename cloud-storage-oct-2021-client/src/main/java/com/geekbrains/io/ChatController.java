package com.geekbrains.io;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class ChatController implements Initializable {

    public ListView<String> listLeft;
    public TextField input;
    public Button upload;
    public Button download;
    private Path root;
    private Path filePath;
    private byte[] buffer;
    private DataInputStream dis;
    private DataOutputStream dos;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buffer = new byte[1024];
        root = Paths.get("root-client");
        filePath = root;
        if (!Files.exists(root)) {
            try {
                Files.createDirectory(root);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        try {
            fillFilesInView();
        } catch (Exception e) {
            e.printStackTrace();
        }

        listLeft.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String fileName = listLeft.getSelectionModel().getSelectedItem();
                if (fileName.equals("Back to previous")){
                    if (filePath.getParent() != null) {
                        filePath = filePath.getParent();
                        try {
                            fillFilesInView();
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    } else {
                        input.setText("no");
                    }
                } else if (Files.isDirectory(filePath.resolve(fileName))) {
                    filePath = Paths.get(filePath.resolve(fileName).toString());
                    try {
                        fillFilesInView();
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else if (!Files.isDirectory(filePath.resolve(fileName))){
                    upload.fire();
                } else {
                    input.setText("Select file! Not directory");
                }
            }
        });

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fillFilesInView() throws Exception {
        listLeft.getItems().clear();
        listLeft.getItems().add("Back to previous");
        List<String> list = Files.list(filePath)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        listLeft.getItems().addAll(list);
    }

    public void sendMessage(ActionEvent actionEvent){
        String fileName = listLeft.getSelectionModel().getSelectedItem();
        filePath = Paths.get(filePath.resolve(fileName).toString());
        List<Path> paths = new ArrayList<>();
        if (Files.isDirectory(filePath)) {
            paths = directoryParsing(filePath);
//            paths.forEach(System.out::println);
//            input.setText("choose file. not directory");
        } else {
            paths.add(filePath);
        }
//        Path filePath = root.resolve(fileName);
        paths.forEach(p -> {
            try {
                if (Files.exists(p)) {
                    String path;
                    if (!p.getParent().equals(root)) {
                        path = p.getParent().toString();
                        System.out.println(path);
                        path = path.replace(root.toString() + "/", "");
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
        filePath = filePath.getParent();
    }

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
