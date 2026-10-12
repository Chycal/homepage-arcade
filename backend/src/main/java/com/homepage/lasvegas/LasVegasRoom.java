package com.homepage.lasvegas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepage.config.JwtUtil;
import com.homepage.lasvegas.model.LasVegasPlayerResult;
import com.homepage.lasvegas.repository.LasVegasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 拉斯维加斯单房间。
 *
 * 与五子棋的 {@code GameRoom} 完全独立，不共享任何状态或代码。
 * 房间由 {@link LasVegasRoomRegistry} 按 roomCode 创建，当前只用默认房间，
 * 但本类本身不假设「只有一个房间」，便于后续扩展为多房间。
 *
 * 消息协议见 docs/las-vegas-design-outline.md §14。
 */
public class LasVegasRoom {

    private static final Logger log = LoggerFactory.getLogger(LasVegasRoom.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CHAT_LIMIT = 30;
    private static final int MAX_MEMBERS = 5;

    /** 房间阶段（与引擎的 PLACING/GAME_OVER 区分开） */
    private enum RoomPhase {
        LOBBY, PLAYING, FINISHED
    }

    /** 一名成员（大厅阶段即存在，开局后对应引擎里的一个座位） */
    private static class Member {
        final String key;          // 客户端标识：登录用户 u:<id>，游客 g:<随机>
        final Long userId;         // 登录用户的数字 ID，游客为 null
        final String username;
        WebSocketSession session;
        boolean connected = true;

        Member(String key, Long userId, String username, WebSocketSession session) {
            this.key = key;
            this.userId = userId;
            this.username = username;
            this.session = session;
        }
    }

    private final String roomCode;
    private final LasVegasRepository repository;
    private final JwtUtil jwtUtil;

    /** 成员，按加入顺序（决定座位） */
    private final Map<String, Member> members = new LinkedHashMap<>();
    /** sessionId -> 成员 key */
    private final Map<String, String> sessionToKey = new LinkedHashMap<>();
    /** 开局后 座位 -> 成员 key */
    private final List<String> seatOrder = new ArrayList<>();
    private final LinkedList<Map<String, Object>> chat = new LinkedList<>();

    private LasVegasGame game;
    private RoomPhase roomPhase = RoomPhase.LOBBY;
    private Long savedMatchId;

    public LasVegasRoom(String roomCode, LasVegasRepository repository, JwtUtil jwtUtil) {
        this.roomCode = roomCode;
        this.repository = repository;
        this.jwtUtil = jwtUtil;
    }

    public String getRoomCode() {
        return roomCode;
    }

    // ------------------------------------------------------------------
    // 入口：连接 / 消息 / 断开
    // ------------------------------------------------------------------

    public void handleConnect(WebSocketSession session) {
        // 仅记录，身份在 join 消息中确定
        log.debug("[{}] 连接 {}", roomCode, session.getId());
    }

    public void handleDisconnect(WebSocketSession session) {
        synchronized (this) {
            String key = sessionToKey.remove(session.getId());
            if (key == null) {
                return;
            }
            if (roomPhase == RoomPhase.LOBBY) {
                // 大厅阶段直接移除，避免掉线者长期占位/占房主
                Member removed = members.remove(key);
                if (removed != null) {
                    log.info("[{}] {} 离开大厅", roomCode, removed.username);
                }
            } else {
                // 对局中保留座位以便重连
                Member member = members.get(key);
                if (member != null) {
                    member.connected = false;
                    member.session = null;
                }
                // 若全员掉线，则放弃这局，房间回到大厅，避免死局永久占位
                boolean anyoneOnline = false;
                for (Member m : members.values()) {
                    if (m.connected) {
                        anyoneOnline = true;
                        break;
                    }
                }
                if (!anyoneOnline) {
                    log.info("[{}] 全员掉线，放弃本局并回到大厅", roomCode);
                    resetToLobby();
                }
            }
            broadcastState();
        }
    }

    /** 清空对局，回到大厅；同时移除已掉线的成员 */
    private void resetToLobby() {
        game = null;
        seatOrder.clear();
        roomPhase = RoomPhase.LOBBY;
        savedMatchId = null;
        members.entrySet().removeIf(e -> !e.getValue().connected);
    }

    public void handleMessage(WebSocketSession session, String payload) {
        Map<String, Object> message;
        try {
            message = MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            sendError(session, "消息格式错误");
            return;
        }
        String type = str(message.get("type"));
        if (type == null) {
            sendError(session, "缺少消息类型");
            return;
        }
        try {
            switch (type) {
                case "join":
                    onJoin(session, message);
                    break;
                case "leave":
                    onLeave(session);
                    break;
                case "start":
                    onStart(session);
                    break;
                case "roll":
                    onRoll(session);
                    break;
                case "choose":
                    onChoose(session, message);
                    break;
                case "restart":
                    onRestart(session);
                    break;
                case "chat":
                    onChat(session, message);
                    break;
                case "state":
                    sendState(session);
                    break;
                default:
                    sendError(session, "未知消息类型: " + type);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(session, e.getMessage());
        } catch (Exception e) {
            log.warn("[{}] 处理 {} 失败: {}", roomCode, type, e.toString());
            sendError(session, "服务器处理失败");
        }
    }

    // ------------------------------------------------------------------
    // 消息处理
    // ------------------------------------------------------------------

    private void onJoin(WebSocketSession session, Map<String, Object> message) {
        String token = str(message.get("token"));
        Long userId = null;
        String username = null;

        // 已登录用户：用 JWT 解析出可信身份（不信任客户端自报的 userId）
        if (token != null && !token.isEmpty()) {
            try {
                userId = jwtUtil.getUserIdFromToken(token);
                username = jwtUtil.extractUsername(token);
            } catch (Exception e) {
                userId = null;
                username = null;
            }
        }

        String guestId = str(message.get("guestId"));
        String key;
        if (userId != null) {
            key = "u:" + userId;
        } else {
            if (guestId == null || guestId.isEmpty()) {
                guestId = "g" + System.nanoTime();
            }
            key = "g:" + guestId;
            username = str(message.get("username"));
            if (username == null || username.isEmpty()) {
                username = "游客" + guestId.substring(Math.max(0, guestId.length() - 4));
            }
        }

        synchronized (this) {
            Member existing = members.get(key);
            if (existing != null) {
                // 重连：换绑 session
                if (existing.session != null) {
                    sessionToKey.remove(existing.session.getId());
                }
                existing.session = session;
                existing.connected = true;
                sessionToKey.put(session.getId(), key);
                log.info("[{}] {} 重新连接", roomCode, existing.username);
            } else {
                if (roomPhase != RoomPhase.LOBBY) {
                    sendError(session, "对局已开始，本局不能加入新玩家");
                    return;
                }
                if (members.size() >= MAX_MEMBERS) {
                    sendError(session, "房间已满（最多 " + MAX_MEMBERS + " 人）");
                    return;
                }
                members.put(key, new Member(key, userId, username, session));
                sessionToKey.put(session.getId(), key);
                log.info("[{}] {} 加入（共 {} 人）", roomCode, username, members.size());
            }
            broadcastState();
        }
    }

    private void onLeave(WebSocketSession session) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                return;
            }
            if (roomPhase == RoomPhase.PLAYING) {
                // 对局中离开只标记掉线，保留座位以便重连
                Member member = members.get(key);
                if (member != null) {
                    member.connected = false;
                    member.session = null;
                }
            } else {
                members.remove(key);
                sessionToKey.remove(session.getId());
            }
            broadcastState();
        }
    }

    private void onStart(WebSocketSession session) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                sendError(session, "请先加入房间");
                return;
            }
            if (roomPhase == RoomPhase.PLAYING) {
                sendError(session, "对局进行中");
                return;
            }
            if (roomPhase == RoomPhase.FINISHED) {
                sendError(session, "本局已结束，请点击「再来一局」");
                return;
            }
            if (!isHost(key)) {
                sendError(session, "只有房主可以开始对局");
                return;
            }
            if (members.size() < LasVegasGame.MIN_PLAYERS) {
                sendError(session, "至少需要 " + LasVegasGame.MIN_PLAYERS + " 名玩家");
                return;
            }

            seatOrder.clear();
            seatOrder.addAll(members.keySet());
            List<String> ids = new ArrayList<>(seatOrder);
            game = new LasVegasGame(ids);
            roomPhase = RoomPhase.PLAYING;
            savedMatchId = null;
            log.info("[{}] 对局开始，{} 名玩家", roomCode, ids.size());
            broadcastState();
        }
    }

    private void onRoll(WebSocketSession session) {
        synchronized (this) {
            String key = requirePlayingTurn(session);
            if (key == null) {
                return;
            }
            game.roll();
            broadcastState();
        }
    }

    private void onChoose(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String key = requirePlayingTurn(session);
            if (key == null) {
                return;
            }
            Object raw = message.get("value");
            if (!(raw instanceof Number)) {
                sendError(session, "缺少点数 value");
                return;
            }
            int value = ((Number) raw).intValue();
            game.choose(value);
            if (game.isOver()) {
                roomPhase = RoomPhase.FINISHED;
                persistResult();
            }
            broadcastState();
        }
    }

    private void onRestart(WebSocketSession session) {
        synchronized (this) {
            if (roomPhase != RoomPhase.FINISHED) {
                sendError(session, "对局尚未结束");
                return;
            }
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                sendError(session, "请先加入房间");
                return;
            }
            // 移除已离开的成员，回到大厅
            resetToLobby();
            broadcastState();
        }
    }

    private void onChat(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            Member member = key == null ? null : members.get(key);
            if (member == null) {
                sendError(session, "请先加入房间");
                return;
            }
            String text = str(message.get("text"));
            if (text == null || text.isBlank()) {
                return;
            }
            if (text.length() > 200) {
                text = text.substring(0, 200);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("username", member.username);
            entry.put("text", text);
            chat.addLast(entry);
            while (chat.size() > CHAT_LIMIT) {
                chat.removeFirst();
            }
            broadcastState();
        }
    }

    /** 校验：对局中、已加入、轮到自己、且已投骰（choose 另行校验） */
    private String requirePlayingTurn(WebSocketSession session) {
        if (roomPhase != RoomPhase.PLAYING || game == null) {
            sendError(session, "对局尚未开始");
            return null;
        }
        String key = sessionToKey.get(session.getId());
        if (key == null) {
            sendError(session, "请先加入房间");
            return null;
        }
        if (!key.equals(game.getCurrentPlayerId())) {
            sendError(session, "还没轮到你");
            return null;
        }
        return key;
    }

    /** 房主 = 仍在线的成员中最早加入者；掉线者不会长期占据房主 */
    private String hostKey() {
        for (Member member : members.values()) {
            if (member.connected) {
                return member.key;
            }
        }
        return null;
    }

    private boolean isHost(String key) {
        return key != null && key.equals(hostKey());
    }

    // ------------------------------------------------------------------
    // 战绩落库
    // ------------------------------------------------------------------

    private void persistResult() {
        if (game == null || savedMatchId != null) {
            return;
        }
        try {
            List<Integer> winnerSeats = game.getWinnerSeats();
            List<String> winnerKeys = new ArrayList<>();
            for (int seat : winnerSeats) {
                winnerKeys.add(seatOrder.get(seat));
            }

            long matchId = repository.createMatch(
                    roomCode,
                    game.getPlayerCount(),
                    game.getRound(),
                    String.join(",", winnerKeys),
                    LocalDateTime.now());

            for (LasVegasGame.Player player : game.getPlayers()) {
                String key = seatOrder.get(player.getSeat());
                Member member = members.get(key);
                boolean winner = winnerSeats.contains(player.getSeat());
                repository.addPlayerResult(matchId, new LasVegasPlayerResult(
                        member != null ? member.userId : null,
                        member != null ? member.username : key,
                        player.getSeat(),
                        player.getMoney(),
                        player.getBanknoteCount(),
                        winner));
            }
            savedMatchId = matchId;
            log.info("[{}] 战绩已保存 matchId={}", roomCode, matchId);
        } catch (Exception e) {
            log.warn("[{}] 战绩保存失败: {}", roomCode, e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 状态广播
    // ------------------------------------------------------------------

    private void broadcastState() {
        for (Member member : members.values()) {
            if (member.connected && member.session != null) {
                sendState(member.session);
            }
        }
    }

    private void sendState(WebSocketSession session) {
        String key = sessionToKey.get(session.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "room_state");
        payload.put("roomCode", roomCode);
        payload.put("phase", roomPhase.name());
        payload.put("isHost", key != null && isHost(key));
        payload.put("you", key);
        payload.put("chat", new ArrayList<>(chat));

        if (game == null) {
            payload.put("players", lobbyPlayers());
            payload.put("casinos", Collections.emptyList());
            payload.put("round", 0);
            payload.put("totalRounds", LasVegasGame.TOTAL_ROUNDS);
            payload.put("canStart", key != null && isHost(key)
                    && members.size() >= LasVegasGame.MIN_PLAYERS);
            payload.put("minPlayers", LasVegasGame.MIN_PLAYERS);
            payload.put("maxPlayers", LasVegasGame.MAX_PLAYERS);
            payload.put("yourRoll", Collections.emptyList());
            payload.put("options", Collections.emptyMap());
            payload.put("canRoll", false);
            payload.put("canChoose", false);
            safeSend(session, payload);
            return;
        }

        payload.put("round", game.getRound());
        payload.put("totalRounds", LasVegasGame.TOTAL_ROUNDS);
        payload.put("players", gamePlayers());
        payload.put("casinos", casinos());
        payload.put("deckSize", game.getDeckSize());
        payload.put("deckTotal", game.getDeckTotalValue());
        payload.put("currentPlayerKey", game.getCurrentPlayerId());
        payload.put("currentPlayerName", usernameOf(game.getCurrentPlayerId()));
        payload.put("lastPayout", payouts());

        boolean myTurn = key != null && key.equals(game.getCurrentPlayerId())
                && roomPhase == RoomPhase.PLAYING;
        payload.put("yourRoll", myTurn ? game.getLastRoll() : Collections.emptyList());
        payload.put("options", myTurn ? game.getOptions() : Collections.emptyMap());
        payload.put("canRoll", myTurn && !game.hasRolled());
        payload.put("canChoose", myTurn && game.hasRolled());

        if (roomPhase == RoomPhase.FINISHED) {
            List<Map<String, Object>> winners = new ArrayList<>();
            for (int seat : game.getWinnerSeats()) {
                String winnerKey = seatOrder.get(seat);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", winnerKey);
                entry.put("username", usernameOf(winnerKey));
                entry.put("money", game.getPlayer(seat).getMoney());
                entry.put("banknotes", game.getPlayer(seat).getBanknoteCount());
                winners.add(entry);
            }
            payload.put("winners", winners);
            payload.put("matchId", savedMatchId);
        }
        safeSend(session, payload);
    }

    private List<Map<String, Object>> lobbyPlayers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String host = hostKey();
        int seat = 0;
        for (Member member : members.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", member.key);
            entry.put("username", member.username);
            entry.put("seat", seat++);
            entry.put("connected", member.connected);
            entry.put("isHost", member.key.equals(host));
            list.add(entry);
        }
        return list;
    }

    private List<Map<String, Object>> gamePlayers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LasVegasGame.Player player : game.getPlayers()) {
            String key = seatOrder.get(player.getSeat());
            Member member = members.get(key);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", key);
            entry.put("username", member != null ? member.username : key);
            entry.put("seat", player.getSeat());
            entry.put("connected", member != null && member.connected);
            entry.put("diceLeft", player.getDiceLeft());
            entry.put("money", player.getMoney());
            entry.put("banknotes", player.getBanknoteCount());
            list.add(entry);
        }
        return list;
    }

    private List<Map<String, Object>> casinos() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int value = 1; value <= LasVegasGame.CASINO_COUNT; value++) {
            List<Integer> notes = game.getCasinoBanknotes(value);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", value);
            entry.put("banknotes", notes);
            entry.put("total", LasVegasBanknotes.totalValue(notes));

            List<Map<String, Object>> dice = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : game.getCasinoDice(value).entrySet()) {
                String key = seatOrder.get(e.getKey());
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("seat", e.getKey());
                d.put("username", usernameOf(key));
                d.put("count", e.getValue());
                dice.add(d);
            }
            entry.put("dice", dice);
            list.add(entry);
        }
        return list;
    }

    private List<Map<String, Object>> payouts() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CasinoPayout payout : game.getLastPayout()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", payout.getDieValue());
            List<String> voided = new ArrayList<>();
            for (int seat : payout.getVoidedSeats()) {
                voided.add(usernameOf(seatOrder.get(seat)));
            }
            entry.put("voided", voided);
            Map<String, Integer> awards = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> award : payout.getAwards().entrySet()) {
                awards.put(usernameOf(seatOrder.get(award.getKey())), award.getValue());
            }
            entry.put("awards", awards);
            list.add(entry);
        }
        return list;
    }

    private String usernameOf(String key) {
        Member member = members.get(key);
        return member != null ? member.username : key;
    }

    // ------------------------------------------------------------------
    // 发送
    // ------------------------------------------------------------------

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", message);
        safeSend(session, payload);
    }

    private void safeSend(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            // 连接可能在发送途中被关闭（IllegalStateException）或写入失败（IOException），
            // 两种都属于正常的并发断开，不应向外抛出
            log.debug("[{}] 发送失败: {}", roomCode, e.toString());
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
