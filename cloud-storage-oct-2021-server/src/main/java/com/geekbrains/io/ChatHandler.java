package com.geekbrains.io;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ChatHandler implements Runnable {

    private static final int BUFFER_SIZE = 1024;

    private byte[] buffer;
    private final Path root;
    private Path filePath;
    private Path clientDir;
    private static int counter = 0;
//    private final String userName;
    private final Server server;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final SimpleDateFormat format;

    public ChatHandler(Socket socket, Server server) throws Exception {
        buffer = new byte[BUFFER_SIZE];
        root = Path.of("root-server");
        filePath = root;
        if (!Files.exists(root)) {
            Files.createDirectory(root);
        }

        this.server = server;
//        counter++;
//        userName = "User_" + counter;
//        clientDir = root.resolve(userName);

//        if (!Files.exists(clientDir)) {
//            Files.createDirectory(clientDir);
//        }

        format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                /*чтение команды в виде массива байтов и дальнейшее е представление в класс*/
                long length = dis.readLong();
                byte[] com = new byte[(int)length];
                for (int i = 0; i < length; i++){
                    com[i] = dis.readByte();
                }
                /*-----------!------------*/
                /*определение типа команды*/
//                System.out.println(operation);
//                switch (operation){
//                    case Command.DOWNLOAD:
//                        fileReceive();
//                        break;
//                    case Command.LIST:
//                        break;
//                    default:
//                        System.out.println("Unknown");
//                        break;
//                }

            }
        } catch (Exception e) {
            System.err.println("Connection was broken");
            e.printStackTrace();
        }
    }

    /*педставление директорий рута сервера в виде списка*/
    private List<String> list() throws IOException{
        List<String> list = Files.list(filePath)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        return list;
    }

    private void fileReceive() throws Exception {
        String fileName = dis.readUTF();
        Path path = Paths.get(dis.readUTF());
        clientDir = root.resolve(path);
        if (!Files.exists(clientDir)) {
            Files.createDirectory(clientDir);
        }
        Path file = clientDir.resolve(fileName);
        System.out.println(file);
        long size = dis.readLong();
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            for (int i = 0; i < (size + BUFFER_SIZE - 1) / BUFFER_SIZE; i++) {
                int read = dis.read(buffer);
                fos.write(buffer, 0, read);
            }
        }
        clientDir = root;
        responseOk();
    }

//    public String getMessage(String msg) {
//        return getTime() + " [" + userName + "]: " + msg;
//    }

    public String getTime() {
        return format.format(new Date());
    }

    private void responseOk() throws Exception {
        dos.writeUTF("File received!");
        dos.flush();
    }

    public void sendMessage(String msg) throws Exception {
        dos.writeUTF(msg);
        dos.flush();
    }
}
