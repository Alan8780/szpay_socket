package com.pay.boss.service;

public interface WsCallBack {
    public void onMessage(int error, String msg);
    public void onError(int error, String msg);
}
