package com.pay.boss.service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

//MWebSocketService
public class MWebSocketService extends WebSocketServer {

    private static int PORT = 2018;
    private static final Log log = LogFactory.getLog(MWebSocketService.class);
    private static MWebSocketService mWebSocketService;
    private static Map<String, WebSocket> webSocketMap;
    private static Map<String, WsCallBack> wsCallBackMap;

    // 获取MWebSocketService实例，第一次调用时会开启Socket服务，默认端口2018，可修改，建议系统启动就调用一次
    public static MWebSocketService getInstance() {
        try {
            if (null == mWebSocketService) {
                synchronized (MWebSocketService.class) {
                    if (null == mWebSocketService) {
                        mWebSocketService = new MWebSocketService(PORT);
                        mWebSocketService.start();
                        String ip = InetAddress.getLocalHost().getHostAddress();
                        int port = mWebSocketService.getPort();
                        log.info(String.format("服务已启动: %s:%d", ip, port));
                        webSocketMap = new HashMap<String, WebSocket>();
                        wsCallBackMap = new HashMap<String, WsCallBack>();
                    }
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return mWebSocketService;
    }


    private MWebSocketService() {

    }

    private MWebSocketService(int port) {
        super(new InetSocketAddress(port));
    }

    private MWebSocketService(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        String resourceDescriptor = webSocket.getResourceDescriptor();
        log.info("onOpen --->" + resourceDescriptor);

        String[] tokens = resourceDescriptor.split("token=");
        if (tokens.length == 2) {
            String token = tokens[1];
            WebSocket webSocketExist = webSocketMap.get(token);
            //将消息发送给每一个客户端
            if (webSocketExist != null) {
                webSocketExist.close();
            }
            webSocketMap.put(token, webSocket);
        }
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        String resourceDescriptor = webSocket.getResourceDescriptor();
        log.info("onClose --->" + resourceDescriptor);

        String[] tokens = resourceDescriptor.split("token=");
        if (tokens.length == 2) {
            String token = tokens[1];
            WebSocket webSocketExist = webSocketMap.get(token);
            if (webSocketExist.equals(webSocket)){
                webSocketMap.remove(token);
            }
            webSocket.close();
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        //服务端接收到消息
        String resourceDescriptor = webSocket.getResourceDescriptor();
        log.info("onMessage --->" + resourceDescriptor + "，message --->" + s);

        //过滤消息，心跳检测
        if ("ping".equalsIgnoreCase(s)) {
            String[] tokens = resourceDescriptor.split("token=");
            //检查是否缓存
            if (tokens.length == 2) {
                String token = tokens[1];
                WebSocket webSocketExist = webSocketMap.get(token);
                if (webSocketExist == null) {
                    webSocket.close();//没有缓存，则断线重连
                }else if (!webSocketExist.equals( webSocket)) {
                    webSocketMap.put(token,webSocket);//缓存与当前不符，更新
                }
            }
            return;
        }

        //无用消息
        if (!s.startsWith("RESULT=")) {
            return;
        }

        try {
            JSONObject jsonData = JSONObject.fromObject(s.replaceFirst("RESULT=", ""));
            String mark = jsonData.optString("mark", "");
            WsCallBack wsCallBack = wsCallBackMap.get(mark);
            if (wsCallBack != null) {
                wsCallBack.onMessage(1, s);
                wsCallBackMap.remove(mark);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        String[] tokens = resourceDescriptor.split("token=");
//        if (tokens.length == 2){
//            String token = tokens[1];
//            WsCallBack wsCallBack = wsCallBackMap.get(token);
//            if (wsCallBack != null){
//                wsCallBack.onMessage(1,s);
//                wsCallBackMap.remove(token);
//            }
//        }
    }

    private static void print(String msg) {
        System.out.println(String.format("[%d] %s", System.currentTimeMillis(), msg));
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        if (null != webSocket) {
            String resourceDescriptor = webSocket.getResourceDescriptor();
            log.info("onError --->" + resourceDescriptor);
//            String[] tokens = resourceDescriptor.split("token=");
//            if (tokens.length == 2) {
//                String token = tokens[1];
//                WsCallBack wsCallBack = wsCallBackMap.get(token);
//                if (wsCallBack != null) {
//                    wsCallBack.onError(1, e.getMessage());
//                    wsCallBackMap.remove(token);
//                }
//            }
            webSocket.close();
        }
        e.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println(String.format("[%d] %s", System.currentTimeMillis(), "onStart"));
    }


    public void setWsCallBack(String uuid, WsCallBack wsCallBack) {

    }


    /**
     * 向APP发送数据，
     * @param accountNo 账号标示，一台手机只允许有一个标示，且不重复
     * @param uuid 订单备注，需要唯一
     * @param message 发送的数据
     * @param wsCallBack block回调函数，APP返回数据后调用
     */
    public void sendToWebSocket(String accountNo, String uuid, String message, WsCallBack wsCallBack) {
        // 获取指定连接的客户端
        WebSocket webSocket = webSocketMap.get(accountNo);
        //将消息发送给一个客户端
        if (webSocket != null) {
            wsCallBackMap.put(uuid, wsCallBack);
            webSocket.send(message);
            log.info("sendToWebSocket --->accountNo = " + accountNo + "，uuid = " + uuid+ "，message = " + message);
        }
    }

    /**
     * 测试用例，无返回
     * @param accountNo 账号标示，一台手机只允许有一个标示，且不重复
     * @param message 发送的数据
     */
    public void sendToWebSocket(String accountNo, String message) {
        // 获取指定连接的客户端
        WebSocket webSocket = webSocketMap.get(accountNo);
        //将消息发送给一个客户端
        if (webSocket != null) {
            webSocket.send(message);
            log.info("sendToWebSocket --->accountNo = " + accountNo + "，message = " + message);
        }
    }


    /**
     * 检查某个订单是否成功，成功后会移除该uuid对应的wsCallBack
     * @param uuid 订单备注
     * @return 结果
     */
    public boolean isSuccess(String uuid) {
        WsCallBack wsCallBack = wsCallBackMap.get(uuid);
        return wsCallBack == null;
    }

    /**
     * 检查某个手机+标示是否离线
     * @param accountNo 账号标示
     * @return
     */
    public boolean isOffline(String accountNo) {
        WebSocket webSocket = webSocketMap.get(accountNo);
        return webSocket == null;
    }

    /**
     * 获取所有在线的设备
     * @return 结果集
     */
    public Set<String> getOnlineAccounts() {
        return new HashSet<>(webSocketMap.keySet());
    }

    /**
     * 向所有在线设备发送信息，无返回
     * @param message 发送的数据
     */
    public void sendToAll(String message) {
        // 获取所有连接的客户端
        Collection<WebSocket> connections = getConnections();
        //将消息发送给每一个客户端
        for (WebSocket client : connections) {
            client.send(message);
        }
    }


}

