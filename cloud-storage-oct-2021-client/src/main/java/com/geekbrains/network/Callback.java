package com.geekbrains.network;

import com.geekbrains.model.AbstractMessage;

import java.io.IOException;

public interface Callback {

    void callback(AbstractMessage msg) throws Exception;

}
