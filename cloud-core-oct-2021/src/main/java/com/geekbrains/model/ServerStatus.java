package com.geekbrains.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ServerStatus extends AbstractMessage{

    private final String name;
    private CommandType status;

    public ServerStatus(
            String name,
            CommandType status) {
        this.name = name;
        this.status = status;
        setType(this.status);
    }
}
