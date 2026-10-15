package com.homepage.config;

import com.homepage.game.GameWebSocketHandler;
import com.homepage.lasvegas.LasVegasWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册两个互相独立的原生 WebSocket 端点。
 *
 * <ul>
 *   <li>{@code /ws/game} → 五子棋（{@link GameWebSocketHandler}）</li>
 *   <li>{@code /ws/lasvegas} → 拉斯维加斯（{@link LasVegasWebSocketHandler}）</li>
 * </ul>
 *
 * 注意拉斯维加斯的路径是连写的 {@code /ws/lasvegas}，不是 {@code /ws/las-vegas}
 * （路由页面用的是带连字符的 /las-vegas，两者别混）。
 *
 * 用的是 Spring 原生 WebSocket 而非 STOMP：消息体就是普通 JSON 字符串，
 * 房间层自己解析，省掉一层协议依赖。
 */
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
