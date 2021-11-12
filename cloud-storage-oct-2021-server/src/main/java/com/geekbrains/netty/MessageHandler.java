package com.geekbrains.netty;

import com.geekbrains.model.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MessageHandler extends SimpleChannelInboundHandler<AbstractMessage> {

    private Path rootServer;
    private byte[] buffer;
    private Path serverFilePath;
    private Path clientDir;

//    private String fileName;
//    private byte[] cache;
//    private int posFrom;
//    private Path file;
//    private int lastPos;
//    private int k = 0;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        rootServer = Paths.get("root-server");
        serverFilePath = rootServer;
        ctx.writeAndFlush(new ListMessage(rootServer));
        buffer = new byte[65536];
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractMessage msg) throws Exception {
//        log.debug("Start processing {}", msg);
        switch (msg.getType()) {
            case FILE_MESSAGE:
                processFile((FileMessage) msg, ctx);
                break;
            case FILE_REQUEST:
                sendFile((FileRequest) msg, ctx);
                break;
            case LIST_REQUEST:
                ListRequest listRequest = (ListRequest) msg;
                listRequest(listRequest.getDir(), ctx);
                break;
            case FILE_DELETE:
                FileDelete file = (FileDelete) msg;
                deleteFile(serverFilePath.resolve(file.getDir()));
                listRequest(serverFilePath.toString(), ctx);
                break;
        }
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
    }

    private void listRequest(String dir, ChannelHandlerContext ctx) throws Exception{
        if (dir.equals("..") && serverFilePath.getParent() != null){
            serverFilePath = serverFilePath.getParent();
        } else {
            if (!dir.contains(rootServer.toString())) {
                serverFilePath = serverFilePath.resolve(dir);
            } else { serverFilePath = Paths.get(dir);}
        }
        ctx.writeAndFlush(new ListMessage(serverFilePath));
    }

    private void sendFile(FileRequest msg, ChannelHandlerContext ctx) {
        Path filePath = Paths.get(msg.getName());
        List<Path> paths = new ArrayList<>();
        if (Files.isDirectory(filePath)) {
            paths = directoryParsing(filePath);
        } else {
            paths.add(filePath);
        }
        paths.forEach(p -> {
            int k = 0;
            if (Files.exists(p)) {
                String path;
                if (!p.getParent().equals(rootServer)) {
                    path = p.getParent().toString();
                    path = path.replace(rootServer.toString() + "/", "");
                } else {
                    path = "";
                }
                boolean isFirstButch = true;
                try {
                    long size = Files.size(p);
                    try (FileInputStream is = new FileInputStream(p.toFile())) {
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            k++;
                            FileMessage message = FileMessage.builder()
                                    .bytes(buffer)
                                    .name(p.getFileName().toString())
                                    .path(path)
                                    .size(size)
                                    .isFirstButch(isFirstButch)
                                    .isFinishBatch(is.available() <= 0)
                                    .endByteNum(read)
                                    .build();
                            ctx.writeAndFlush(message);
                            isFirstButch = false;
                            TimeUnit.MILLISECONDS.sleep(1);
                        }
                    } catch (Exception e) {
                        log.error("e:", e);
                    }
                } catch (IOException e){
                    log.error("e", e);
                }
            }
        });
//        serverFilePath = serverFilePath.getParent();
    }

    private void processFile(FileMessage msg, ChannelHandlerContext ctx) throws Exception {
        String fileName = msg.getName();
        Path path = Paths.get(msg.getPath());
        clientDir = rootServer.resolve(path);
        if (!Files.exists(clientDir)) {
            Files.createDirectory(clientDir);
        }
        Path file = clientDir.resolve(fileName);
        clientDir = rootServer;

        if (msg.isFirstButch()) {
            Files.deleteIfExists(file);
        }

        try(FileOutputStream os = new FileOutputStream(file.toFile(), true)) {
            os.write(msg.getBytes(), 0, msg.getEndByteNum());
        }

        if (msg.isFinishBatch()) {
            ctx.writeAndFlush(new ListMessage(serverFilePath));
        }

        /**в теории это должен быть буфер, байты в который должны записываться быстрее чем в файл и это бы решило проблему битых файлов в конце, но файлы все равно бьються
         * логика в том, что в переменную в памяти ону будут записываться куда быстрее чем файл, но как-то не получилось
        if (msg.isFirstButch()) {
            fileName = msg.getName();
            posFrom = 0;
            lastPos = 0;
            Path path = Paths.get(msg.getPath());
            clientDir = rootServer.resolve(path);

            if (!Files.exists(clientDir)) {
                Files.createDirectory(clientDir);
            }
            file = clientDir.resolve(fileName);
            clientDir = rootServer;

            Files.deleteIfExists(file);
            cache = new byte[134217728];
            System.arraycopy(msg.getBytes(), 0, cache, posFrom, msg.getEndByteNum());
            k++;
        } else {
            log.info("posFrom {}", posFrom);
            log.info("getEndByteNum {}", msg.getEndByteNum());
            if (cache.length - posFrom < msg.getEndByteNum()){
                try (FileOutputStream os = new FileOutputStream(file.toFile(), true)) {
                    os.write(cache, 0, posFrom);
                }
                cache = new byte[134217728];
                lastPos += posFrom;
                posFrom = 0;
                log.info("Written {} bytes", posFrom);
            }
            System.arraycopy(msg.getBytes(), 0, cache, posFrom, msg.getEndByteNum());
            posFrom += msg.getEndByteNum();
            k++;
        }

        if (msg.isFinishBatch()) {
            try (FileOutputStream os = new FileOutputStream(file.toFile(), true)) {
                log.info("lastPos {}", lastPos);
                log.info("cache.length {}", cache.length);

                os.write(cache, 0, msg.getEndByteNum());
            }
            ctx.writeAndFlush(new ListMessage(rootServer));
        }
        log.info("array copied {}", k);
         */
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
