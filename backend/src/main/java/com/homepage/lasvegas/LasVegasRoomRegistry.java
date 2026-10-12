package com.homepage.lasvegas;

import com.homepage.config.JwtUtil;
import com.homepage.lasvegas.repository.LasVegasRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 房间注册表。
 *
 * 当前只使用默认房间，但按 roomCode 管理，
 * 后续要做多房间时只需在 WebSocket 握手时读取 room 参数即可，无需改动 {@link LasVegasRoom}。
 */
@Component
public class LasVegasRoomRegistry {

    /** 默认房间号 */
    public static final String DEFAULT_ROOM = "default";

    private final ConcurrentMap<String, LasVegasRoom> rooms = new ConcurrentHashMap<>();
    private final LasVegasRepository repository;
    private final JwtUtil jwtUtil;

    public LasVegasRoomRegistry(LasVegasRepository repository, JwtUtil jwtUtil) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
    }

    /** 取得（必要时创建）指定房间 */
    public LasVegasRoom room(String roomCode) {
        String code = (roomCode == null || roomCode.isBlank()) ? DEFAULT_ROOM : roomCode.trim();
        return rooms.computeIfAbsent(code,
                c -> new LasVegasRoom(c, repository, jwtUtil));
    }

    /** 默认房间 */
    public LasVegasRoom defaultRoom() {
        return room(DEFAULT_ROOM);
    }

    /** 当前已创建的房间数（便于观测） */
    public int roomCount() {
        return rooms.size();
    }
}
