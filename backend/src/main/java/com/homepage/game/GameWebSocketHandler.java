package com.homepage.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * WebSocket 入口：连接/消息/断线 → GameRoom
 */
@Component
public class GameWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final GameRoom room;

    public GameWebSocketHandler(GameRoom room) {
        this.room = room;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS 连接: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage) {
            room.handleMessage(session, ((TextMessage) message).getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("WS 传输错误 {}: {}", session.getId(), ex.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS 断开: {}", session.getId());
        room.handleDisconnect(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
