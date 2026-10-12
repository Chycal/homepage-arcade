package com.homepage.lasvegas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

/**
 * 拉斯维加斯 WebSocket 入口：/ws/lasvegas
 *
 * 与五子棋的 GameWebSocketHandler 独立；支持 ?room=CODE 以便后续多房间。
 */
@Component
public class LasVegasWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LasVegasWebSocketHandler.class);
    private final LasVegasRoomRegistry registry;

    public LasVegasWebSocketHandler(LasVegasRoomRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        room(session).handleConnect(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage) {
            room(session).handleMessage(session, ((TextMessage) message).getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("拉斯维加斯 WS 传输错误 {}: {}", session.getId(), ex.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        room(session).handleDisconnect(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /** 从 ?room=CODE 解析房间；缺省为默认房间 */
    private LasVegasRoom room(WebSocketSession session) {
        String code = null;
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String pair : uri.getQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "room".equals(kv[0])) {
                    code = kv[1];
                }
            }
        }
        return registry.room(code);
    }
}
