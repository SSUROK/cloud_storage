package com.geekbrains.io;

import com.geekbrains.model.FileMessage;
import com.geekbrains.network.Net;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static javafx.application.Platform.runLater;

@Slf4j
@Builder
public class SendFile extends Task<List<Path>> {

    private byte[] buffer;
    private String name;
    private String fileName;
    private Path path;
    private Path rootPath;
    private Net net;

    @Override
    protected List<Path> call() throws Exception {
        buffer = new byte[8192];
        List<Path> paths = new ArrayList<>();
        path = Paths.get(path.resolve(fileName).toString());
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
                boolean isFirstButch = true;
                long size = 0;
                try {
                    size = Files.size(p);
                } catch (IOException e) {
                    log.error("e:", e);
                }
                try (FileInputStream fis = new FileInputStream(p.toFile())) {
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
//                        log.info(fileName, path, size);
                        FileMessage message = FileMessage.builder()
                                .bytes(buffer)
                                .name(p.getFileName().toString())
                                .path(path)
                                .size(size)
                                .isFirstButch(isFirstButch)
                                .isFinishBatch(fis.available() <= 0)
                                .endByteNum(read)
                                .build();
                        net.send(message);
                        this.updateProgress(read, size);
//                        System.out.println(read);
//                        runLater(()->{
//                            progressBar.setProgress((double) r/buffer.length);
//                        });
                        isFirstButch = false;
                    }
                } catch (Exception e) {
//                    log.error("e:", e);
                }
            }
        });
//        progressBar.setVisible(false);
//        clientFilePath = clientFilePath.getParent();
        return paths;
    }

    public void setFileToSend(
            String fileName,
            Path path,
            Path rootPath,
            Net net){
        this.fileName = fileName;
        this.path = path;
        this.rootPath = rootPath;
        this.net = net;
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
