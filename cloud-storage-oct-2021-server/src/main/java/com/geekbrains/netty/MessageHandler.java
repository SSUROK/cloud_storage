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
import java.util.List;

@Slf4j
public class MessageHandler extends SimpleChannelInboundHandler<AbstractMessage> {

    private Path rootServer;
    private byte[] buffer;
    private Path serverFilePath;
    private Path clientDir;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        rootServer = Paths.get("root-server");
        serverFilePath = rootServer;
        ctx.writeAndFlush(new ListMessage(rootServer));
        buffer = new byte[8192];
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AbstractMessage msg) throws Exception {
        log.debug("Start processing {}", msg);
        switch (msg.getType()) {
            case FILE_MESSAGE:
                processFile((FileMessage) msg, ctx);
                break;
            case FILE_REQUEST:
                sendFile((FileRequest) msg, ctx);
                break;
            case LIST_REQUEST:
                ListRequest listRequest = (ListRequest) msg;
                if (listRequest.getDir().equals("..")){
                    rootServer = rootServer.getParent();
                } else {
                    if (!listRequest.getDir().contains(rootServer.toString())) {
                        rootServer = rootServer.resolve(listRequest.getDir());
                    } else { rootServer = Paths.get(listRequest.getDir());}
                }
                ctx.writeAndFlush(new ListMessage(rootServer));
                break;
        }
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
                        }
                    } catch (Exception e) {
                        log.error("e:", e);
                    }
                } catch (IOException e){
                    log.error("e", e);
                }
            }
        });
        System.out.println(serverFilePath);
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
            ctx.writeAndFlush(new ListMessage(rootServer));
        }
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
