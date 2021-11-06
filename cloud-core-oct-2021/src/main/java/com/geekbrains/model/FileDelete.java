package com.geekbrains.model;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class FileDelete extends AbstractMessage {

    private final String dir;

    public FileDelete(String dir){
        setType(CommandType.FILE_DELETE);
        this.dir = dir;
    }
}
