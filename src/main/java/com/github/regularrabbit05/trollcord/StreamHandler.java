package com.github.regularrabbit05.trollcord;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.util.function.Function;

public class StreamHandler implements AudioSendHandler {
    private final Runnable callback;
    private final Function<Integer, byte[]> feed;
    private int cursor;

    @Override
    public boolean canProvide() {
        if (cursor < 0) {
            if (callback != null) callback.run();
            return false;
        }
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        byte[] data = feed.apply(cursor);
        if (data == null) {
            cursor = -1;
            return null;
        }
        cursor += data.length;
        return ByteBuffer.wrap(data);
    }

    public StreamHandler(Function<Integer, byte[]> feed, Runnable callback) {
        this.callback = callback;
        this.feed = feed;
        this.cursor = 0;
    }
}
