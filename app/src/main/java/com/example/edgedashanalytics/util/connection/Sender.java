package com.example.edgedashanalytics.util.connection;

import android.os.Handler;

public class Sender {
    public static final int port = 5555;
    private SenderThread thread;
    public String ip;

    public Sender(String ip) {
        this.ip = ip;
    }

    public void run() {
        thread = new SenderThread(ip, port);
        thread.start();
    }

    public Handler getHandler() {
        return thread.getHandler();
    }

    public long getScore() {
        return thread.getScore();
    }

    public void setScore(long score) {
        thread.setScore(score);
    }
}
