package com.geekbrains.nio;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


// ls -> список файлов в текущей папке +
// cat file -> вывести на экран содержание файла +
// cd path -> перейти в папку
// touch file -> создать пустой файл
public class NioServer {

    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer buffer;
    private Path root;


    public NioServer() throws Exception {
        buffer = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open(); // accept -> SocketChannel
        server.bind(new InetSocketAddress(8189));
        selector = Selector.open();
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        root = Paths.get("root");
        if (!Files.exists(root)) {
            try {
                Files.createDirectory(root);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws Exception {

        SocketChannel channel = (SocketChannel) key.channel();

        StringBuilder sb = new StringBuilder();

        while (true) {
            int read = channel.read(buffer);
            if (read == -1) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();
        }

        //sb.append(" ");
        String result = sb.toString();
        String[] str = result.split(" ");
        switch (str[0].trim()){
            case "ls":
                fillFilesInView(channel);
                break;
            case "cat":
                cat(channel, str[1].trim());
                break;
            case "cd":
                cd(channel, str[1].trim());
                break;
            case "touch":
                break;
            default:
                channel.write(ByteBuffer.wrap("Unknown command\n".getBytes(StandardCharsets.UTF_8)));
                break;
        }
        //channel.write(ByteBuffer.wrap(result.getBytes(StandardCharsets.UTF_8)));
    }

    private void handleAccept(SelectionKey key) throws Exception {
        SocketChannel channel = server.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ, "Hello world!");
    }

    private void fillFilesInView(SocketChannel channel) throws Exception {
        List<String> list = Files.list(root)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        for (String file : list){
            channel.write(ByteBuffer.wrap((file + "\n").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void cat(SocketChannel channel, String fileName)  throws IOException {
        Path filePath = root.resolve(fileName);
        if (Files.exists(filePath)) {
            try (FileReader fr = new FileReader(filePath.toFile())) {
                char[] buf = new char[256];
                int c;
                while ((c = fr.read(buf)) > 0) {

                    if (c < 256) {
                        buf = Arrays.copyOf(buf, c);
                    }
                    channel.write(ByteBuffer.wrap((String.valueOf(buf) + "\n").getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private void cd(SocketChannel channel, String fileName) throws Exception{
        root = Paths.get(root.resolve(fileName).toString());
        fillFilesInView(channel);
    }

    private void touch(SocketChannel channel){

    }


    public static void main(String[] args) throws Exception {
        new NioServer();
    }
}
