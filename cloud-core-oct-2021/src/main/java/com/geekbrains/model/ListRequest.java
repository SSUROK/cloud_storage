package com.geekbrains.model;

import lombok.Getter;
import lombok.ToString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@ToString
public class ListRequest extends AbstractMessage {

    private final String dir;

    public ListRequest(String dir) throws Exception {
        setType(CommandType.LIST_REQUEST);
        this.dir = dir;
    }
}
