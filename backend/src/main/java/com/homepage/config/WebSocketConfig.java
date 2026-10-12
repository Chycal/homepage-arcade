package com.homepage.config;

import com.homepage.game.GameWebSocketHandler;
import com.homepage.lasvegas.LasVegasWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final LasVegasWebSocketHandler lasVegasHandler;

    public WebSocketConfig(GameWebSocketHandler handler,
                           LasVegasWebSocketHandler lasVegasHandler) {
        this.handler = handler;
        this.lasVegasHandler = lasVegasHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 五子棋（原有，保持不变）
        registry.addHandler(handler, "/ws/game").setAllowedOrigins("*");
        // 拉斯维加斯（独立端点，互不影响）
        registry.addHandler(lasVegasHandler, "/ws/lasvegas").setAllowedOrigins("*");
    }
}
