package com.geekbrains.classes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Command implements Serializable {

    public final static String LIST = "list";

    public final static String DOWNLOAD = "download";

    private String body;

    private final String command;

    public Command(String body, String command){
        this.body = body;
        this.command = command;
    }

    public Command(String command){
        this.command = command;
    }

    public String getBody() {
        return body;
    }

    public String getCommand() {
        return command;
    }

    public byte[] getByteArray() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        return bos.toByteArray();
    }

}
