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
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    /**
     * 同一个人两次「扔番茄」之间的最小间隔（毫秒）。
     *
     * 番茄是纯表情，不影响对局，但会广播给全房间并触发动画，
     * 不节流的话一个人狂点就能把别人的屏幕刷满，所以服务端强制限流。
     */
    private static final long TOMATO_COOLDOWN_MS = 600;

    /**
     * 玩家颜色（下标即 colorIndex）。
     *
     * 与前端 {@code DICE_COLORS} 保持一致；颜色在「加入大厅」时就固定下来，
     * 不随座位或他人进出而改变，这样每个玩家都能稳定认出自己的颜色。
     */
    static final String[] COLORS = {"#e53935", "#1e88e5", "#43a047", "#fb8c00", "#8e24aa"};
    /** 颜色中文名，用于「你的颜色是●红」这类提示 */
    static final String[] COLOR_NAMES = {"红", "蓝", "绿", "橙", "紫"};

    /**
     * AI 每步之间的停顿（毫秒）。
     * 不留停顿的话，AI 会在一次广播里把整轮走完，玩家只能看到结果，
     * 体验上像是「卡了一下」。留一点间隔才能看清它逐颗落子。
     * 测试里会调成 0，避免等待。
     */
    private long aiStepDelayMs = 700;

    /** 房间阶段（与引擎的 PLACING/GAME_OVER 区分开） */
    private enum RoomPhase {
        LOBBY, PLAYING, FINISHED
    }

    /** 一名成员（大厅阶段即存在，开局后对应引擎里的一个座位） */
    private static class Member {
        final String key;          // 客户端标识：登录用户 u:<id>，游客 g:<随机>，AI ai:<序号>
        final Long userId;         // 登录用户的数字 ID，游客与 AI 为 null
        final String username;
        final boolean ai;          // 是否为电脑对手
        final LasVegasAi.Style aiStyle;
        final int colorIndex;      // 玩家颜色，加入时固定
        WebSocketSession session;  // AI 没有连接，恒为 null
        boolean connected = true;
        /**
         * 托管：真人中途退出后由 AI 接手他的座位。
         *
         * 否则「离开」会把座位留在对局里却没人能操作，剩下的玩家会被永久卡住。
         * 与 {@link #ai} 的区别是：托管者原本是真人，重连后可以拿回控制权。
         */
        boolean autopilot;

        Member(String key, Long userId, String username, WebSocketSession session, int colorIndex) {
            this(key, userId, username, session, colorIndex, false, null);
        }

        Member(String key, Long userId, String username, WebSocketSession session, int colorIndex,
               boolean ai, LasVegasAi.Style aiStyle) {
            this.key = key;
            this.userId = userId;
            this.username = username;
            this.session = session;
            this.colorIndex = colorIndex;
            this.ai = ai;
            this.aiStyle = aiStyle == null ? LasVegasAi.Style.SOLID : aiStyle;
        }
    }

    private final String roomCode;
    private final LasVegasRepository repository;
    private final JwtUtil jwtUtil;

    /** 成员，按加入顺序（决定座位） */
    private final Map<String, Member> members = new LinkedHashMap<>();
    /** sessionId -> 成员 key */
    private final Map<String, String> sessionToKey = new LinkedHashMap<>();
    /**
     * 观战者：sessionId -> session。
     *
     * 观战者只读：不在 {@link #members} 里，因此不占座位、不参与对局，
     * 也无法通过 {@code sessionToKey} 触发任何操作。任何进入网页的人都能观战，
     * 但对局中的房间不会再接纳新玩家。
     */
    private final Map<String, Spectator> spectators = new LinkedHashMap<>();
    /** 开局后 座位 -> 成员 key */
    private final List<String> seatOrder = new ArrayList<>();
    private final LinkedList<Map<String, Object>> chat = new LinkedList<>();

    /**
     * 扔番茄限流：投掷者 key -> 上次投掷时间（{@link System#currentTimeMillis()}）。
     *
     * 与聊天不同，番茄是动画事件而不是状态，不能塞进 {@code room_state}：
     * 状态是「当前是什么样」，动画是「刚刚发生了什么」。所以它单独作为
     * {@code tomato} 消息广播，前端收到就播一次动画，错过就错过（例如断线期间）。
     */
    private final Map<String, Long> lastTomatoAt = new LinkedHashMap<>();

    private LasVegasGame game;
    private RoomPhase roomPhase = RoomPhase.LOBBY;
    private Long savedMatchId;

    /** AI 的随机源（决策用） */
    private final Random aiRandom = new Random();
    /** AI 命名序号 */
    private int aiSeq = 0;
    /** 驱动 AI 回合的调度器，按需创建 */
    private ScheduledExecutorService aiExecutor;
    /** 是否已有一个待执行的 AI 行动，避免重复排程导致连走两步 */
    private boolean aiTurnScheduled;

    public LasVegasRoom(String roomCode, LasVegasRepository repository, JwtUtil jwtUtil) {
        this.roomCode = roomCode;
        this.repository = repository;
        this.jwtUtil = jwtUtil;
    }

    public String getRoomCode() {
        return roomCode;
    }

    /** 仅供测试：调整 AI 每步间隔，避免测试里空等 */
    void setAiStepDelayMs(long delayMs) {
        this.aiStepDelayMs = Math.max(0, delayMs);
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
            // 观战者断开：直接摘掉即可，不影响任何对局状态
            if (spectators.remove(session.getId()) != null) {
                log.debug("[{}] 观战者断开 {}", roomCode, session.getId());
                return;
            }
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
                pruneAiIfNoHuman();
            } else {
                // 对局中保留座位以便重连；同时交给 AI 托管，避免掉线者把对局卡死
                Member member = members.get(key);
                if (member != null) {
                    member.connected = false;
                    member.session = null;
                    if (!member.ai) {
                        member.autopilot = true;
                    }
                }
                // 若全员掉线，则放弃这局，房间回到大厅，避免死局永久占位
                // 注意：AI 成员恒为「在线」，这里必须只看真人，否则房间永远等不到回收
                if (!anyHumanOnline()) {
                    log.info("[{}] 全员掉线，放弃本局并回到大厅", roomCode);
                    resetToLobby(false);
                }
            }
            broadcastState();
            // 掉线者可能正好是当前玩家，托管要立刻接手，否则对局会停住
            scheduleAiTurn();
        }
    }

    /**
     * 观战者：只读连接。
     *
     * 不占座位、不参与对局、不能触发任何操作，只是持续接收 {@code room_state}。
     * 任何打开网页的人都能看到房间里的对战，但只有参赛者能游玩。
     */
    private static class Spectator {
        final WebSocketSession session;
        final String username;   // 仅用于日志/人数展示，可为 null

        Spectator(WebSocketSession session, String username) {
            this.session = session;
            this.username = username;
        }
    }

    /** 清空对局，回到大厅；同时移除已掉线的成员 */
    private void resetToLobby() {
        resetToLobby(true);
    }

    /**
     * 清空对局，回到大厅。
     *
     * @param keepAi true 表示保留电脑对手（房主点「再来一局」时希望原班人马重开）；
     *               false 表示连 AI 一起清掉（真人全部掉线、房间已无人时没有留着的意义）
     */
    private void resetToLobby(boolean keepAi) {
        game = null;
        seatOrder.clear();
        roomPhase = RoomPhase.LOBBY;
        savedMatchId = null;
        aiTurnScheduled = false;
        cancelAiExecutor();
        members.entrySet().removeIf(e -> !e.getValue().connected || (e.getValue().ai && !keepAi));
        renumberAi();
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
                case "add_ai":
                    onAddAi(session, message);
                    break;
                case "remove_ai":
                    onRemoveAi(session, message);
                    break;
                case "chat":
                    onChat(session, message);
                    break;
                case "throw_tomato":
                    onThrowTomato(session, message);
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
                // 重连：换绑 session。旧连接（例如同一账号的另一个标签页）
                // 降级为观战者，而不是留成一个收不到消息的幽灵连接。
                if (existing.session != null && !existing.session.getId().equals(session.getId())) {
                    WebSocketSession old = existing.session;
                    sessionToKey.remove(old.getId());
                    if (old.isOpen()) {
                        spectators.put(old.getId(), new Spectator(old, existing.username));
                    }
                }
                existing.session = session;
                existing.connected = true;
                // 本人回来了，收回托管控制权
                existing.autopilot = false;
                sessionToKey.put(session.getId(), key);
                spectators.remove(session.getId());
                log.info("[{}] {} 重新连接", roomCode, existing.username);
            } else {
                boolean spectateOnly = Boolean.TRUE.equals(message.get("spectate"));
                if (spectateOnly) {
                    becomeSpectator(session, username);
                    log.info("[{}] {} 以观战身份进入", roomCode, username);
                } else if (roomPhase != RoomPhase.LOBBY) {
                    // 对局进行中不再接纳新玩家，但可以观战 —— 任何进入网页的人都能看到对局
                    becomeSpectator(session, username);
                    log.info("[{}] {} 对局已开始，转为观战", roomCode, username);
                } else if (members.size() >= MAX_MEMBERS) {
                    // 满员时也不拒绝，改为观战，避免「进来看不到任何东西」
                    becomeSpectator(session, username);
                    log.info("[{}] 房间已满，{} 转为观战", roomCode, username);
                } else {
                    members.put(key, new Member(key, userId, username, session, nextColorIndex()));
                    sessionToKey.put(session.getId(), key);
                    spectators.remove(session.getId());
                    log.info("[{}] {} 加入（共 {} 人）", roomCode, username, members.size());
                }
            }
            broadcastState();
        }
    }

    private void onLeave(WebSocketSession session) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                // 观战者「离开」：停止接收状态即可。给他发一份匿名状态，
                // 前端据此把自己切回「未加入」的界面。
                if (spectators.remove(session.getId()) != null) {
                    sendState(session);
                }
                return;
            }
            if (roomPhase == RoomPhase.PLAYING) {
                // 对局中离开：保留座位（重连可继续），但立刻交给 AI 托管，
                // 否则这个座位没人能操作，剩下的玩家会被永久卡住。
                Member member = members.get(key);
                if (member != null) {
                    member.connected = false;
                    member.session = null;
                    if (!member.ai) {
                        member.autopilot = true;
                    }
                }
                // 真人全走光就没必要留着这局（AI 会一直卡在等人投骰）
                if (!anyHumanOnline()) {
                    log.info("[{}] 真人已全部离开，放弃本局并回到大厅", roomCode);
                    resetToLobby(false);
                    becomeSpectator(session, null);
                    broadcastState();
                    return;
                }
            } else {
                members.remove(key);
                sessionToKey.remove(session.getId());
                pruneAiIfNoHuman();
            }
            // 关键：离开者自己也要收到新状态，否则他的页面会停留在旧画面，
            // 仍然显示「自己在房间里」。这里把他转为观战者，于是他能继续看这局，
            // 但不再占座位、不能操作。
            becomeSpectator(session, null);
            log.info("[{}] {} 离开房间，转为观战", roomCode, key);
            broadcastState();
            // 若正好轮到他，托管要立刻接手，否则剩下的玩家会被卡住
            scheduleAiTurn();
        }
    }

    /** 把一个连接登记为观战者（幂等）；会先摘掉它的成员身份 */
    private void becomeSpectator(WebSocketSession session, String username) {
        String oldKey = sessionToKey.remove(session.getId());
        if (oldKey != null) {
            Member member = members.get(oldKey);
            if (member != null) {
                member.connected = false;
                member.session = null;
            }
        }
        spectators.put(session.getId(), new Spectator(session, username));
    }

    /** 是否还有在线的真人成员（AI 恒为「在线」，不能算） */
    private boolean anyHumanOnline() {
        for (Member m : members.values()) {
            if (m.connected && !m.ai) {
                return true;
            }
        }
        return false;
    }

    /** 取一个当前未被占用的颜色下标，保证同房间内玩家颜色两两不同 */
    private int nextColorIndex() {
        boolean[] used = new boolean[COLORS.length];
        for (Member member : members.values()) {
            if (member.colorIndex >= 0 && member.colorIndex < used.length) {
                used[member.colorIndex] = true;
            }
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return members.size() % COLORS.length;
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
            scheduleAiTurn();
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
            // 一轮结束后会轮到下一轮的先手，可能正好是 AI
            scheduleAiTurn();
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

    // ------------------------------------------------------------------
    // 电脑对手（AI）
    // ------------------------------------------------------------------

    /**
     * 房主添加一个电脑对手。
     *
     * 可选 {@code style}: solid / bold / wild，缺省 solid。
     * 仅在未开局时可用；AI 与真人一样占一个座位，同样受 5 人上限约束。
     */
    private void onAddAi(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                sendError(session, "请先加入房间");
                return;
            }
            if (!isHost(key)) {
                sendError(session, "只有房主可以添加电脑对手");
                return;
            }
            if (roomPhase != RoomPhase.LOBBY) {
                sendError(session, "对局已开始，不能再添加电脑对手");
                return;
            }
            if (members.size() >= MAX_MEMBERS) {
                sendError(session, "房间已满（最多 " + MAX_MEMBERS + " 人）");
                return;
            }
            LasVegasAi.Style style = LasVegasAi.Style.of(str(message.get("style")));
            aiSeq++;
            String aiKey = "ai:" + aiSeq;
            String aiName = "电脑" + aiSeq + "（" + style.getLabel() + "）";
            // AI 没有 WebSocket 连接，session 恒为 null；safeSend/broadcastState 会跳过它
            members.put(aiKey, new Member(aiKey, null, aiName, null, nextColorIndex(), true, style));
            log.info("[{}] 添加电脑对手 {}（共 {} 人）", roomCode, aiName, members.size());
            broadcastState();
        }
    }

    /** 房主移除一个电脑对手（只能移除 AI，不能移除真人） */
    private void onRemoveAi(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            if (key == null) {
                sendError(session, "请先加入房间");
                return;
            }
            if (!isHost(key)) {
                sendError(session, "只有房主可以移除电脑对手");
                return;
            }
            if (roomPhase != RoomPhase.LOBBY) {
                sendError(session, "对局已开始，不能移除电脑对手");
                return;
            }
            String target = str(message.get("key"));
            Member member = target == null ? null : members.get(target);
            if (member == null || !member.ai) {
                sendError(session, "找不到该电脑对手");
                return;
            }
            members.remove(target);
            renumberAi();
            log.info("[{}] 移除电脑对手 {}", roomCode, member.username);
            broadcastState();
        }
    }

    /** 重排 AI 名字，保证编号连续（电脑1、电脑2…）。就地替换，保持大厅显示顺序不变。 */
    private void renumberAi() {
        int seq = 0;
        for (Map.Entry<String, Member> entry : new ArrayList<>(members.entrySet())) {
            Member member = entry.getValue();
            if (!member.ai) {
                continue;
            }
            seq++;
            String name = "电脑" + seq + "（" + member.aiStyle.getLabel() + "）";
            if (!name.equals(member.username)) {
                // key 已存在，put 不会改变 LinkedHashMap 的顺序
                members.put(entry.getKey(),
                        new Member(member.key, null, name, null, member.colorIndex, true, member.aiStyle));
            }
        }
        aiSeq = seq;
    }

    /**
     * 大厅里如果已经没有真人，就把电脑对手一并清掉。
     *
     * 否则 AI 会永远占着房间：真人全走光后房间既不空、也没人能操作，
     * 而这些「永远在线」的成员又会让「全员掉线」的回收逻辑失效。
     */
    private void pruneAiIfNoHuman() {
        for (Member member : members.values()) {
            if (!member.ai) {
                return;
            }
        }
        if (!members.isEmpty()) {
            log.info("[{}] 大厅已无真人，清空电脑对手", roomCode);
            members.clear();
        }
        aiSeq = 0;
    }

    // ------------------------------------------------------------------
    // AI 回合驱动
    // ------------------------------------------------------------------

    /**
     * 若当前轮到 AI，则排程它的行动。
     *
     * 每次状态变化（开局、落子、重开）后都要调用，AI 才会接着走。
     * 用 {@code aiTurnScheduled} 防止重复排程导致 AI 连走两步。
     */
    private void scheduleAiTurn() {
        if (roomPhase != RoomPhase.PLAYING || game == null || game.isOver()) {
            return;
        }
        if (aiTurnScheduled) {
            return;
        }
        String currentKey = game.getCurrentPlayerId();
        Member current = currentKey == null ? null : members.get(currentKey);
        if (!isAutomated(current)) {
            return;
        }
        aiTurnScheduled = true;
        try {
            aiExecutor().schedule(this::runAiTurn, aiStepDelayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // 房间正在回收，忽略即可
            aiTurnScheduled = false;
        }
    }

    /**
     * 该座位是否由程序代打。
     *
     * 包括两种：房主添加的电脑对手，以及真人中途退出后被托管的座位。
     * 后者原本是真人，重连后会取消托管并交还控制权。
     */
    private boolean isAutomated(Member member) {
        return member != null && (member.ai || member.autopilot);
    }

    /** 在调度线程上执行一次 AI 行动 */
    private void runAiTurn() {
        synchronized (this) {
            aiTurnScheduled = false;
            if (roomPhase != RoomPhase.PLAYING || game == null || game.isOver()) {
                return;
            }
            String currentKey = game.getCurrentPlayerId();
            Member current = currentKey == null ? null : members.get(currentKey);
            if (!isAutomated(current)) {
                return;
            }
            try {
                if (!game.hasRolled()) {
                    game.roll();
                } else {
                    int value = LasVegasAi.chooseValue(game, game.getLastRoll(),
                            current.aiStyle, aiRandom);
                    game.choose(value);
                    if (game.isOver()) {
                        roomPhase = RoomPhase.FINISHED;
                        persistResult();
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                // 引擎拒绝（例如房间刚被重置）：放弃这步，避免调度器里抛出异常
                log.warn("[{}] AI 行动被拒绝: {}", roomCode, e.getMessage());
                return;
            } catch (Exception e) {
                log.warn("[{}] AI 行动失败: {}", roomCode, e.toString());
                return;
            }
            broadcastState();
            scheduleAiTurn();
        }
    }

    private ScheduledExecutorService aiExecutor() {
        if (aiExecutor == null || aiExecutor.isShutdown()) {
            aiExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "lasvegas-ai-" + roomCode);
                t.setDaemon(true);
                return t;
            });
        }
        return aiExecutor;
    }

    private void cancelAiExecutor() {
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
            aiExecutor = null;
        }
    }

    private void onChat(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String key = sessionToKey.get(session.getId());
            Member member = key == null ? null : members.get(key);
            Spectator spectator = spectators.get(session.getId());
            if (member == null && spectator == null) {
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
            // 观战者也能发言（聊天属于围观互动，不是对局操作），但会带上「观众」标记
            if (member != null) {
                entry.put("username", member.username);
                entry.put("color", COLORS[member.colorIndex % COLORS.length]);
                entry.put("colorName", COLOR_NAMES[member.colorIndex % COLOR_NAMES.length]);
                entry.put("spectator", false);
            } else {
                String name = spectator.username;
                entry.put("username", name == null || name.isEmpty() ? "观众" : name);
                entry.put("spectator", true);
            }
            entry.put("text", text);
            chat.addLast(entry);
            while (chat.size() > CHAT_LIMIT) {
                chat.removeFirst();
            }
            broadcastState();
        }
    }

    /**
     * 扔番茄：向房间广播一个「A 把番茄扔向 B」的动画事件。
     *
     * 纯表情互动，不改变任何对局状态，所以既不写 {@code room_state} 也不落库。
     * 成员和观战者都能扔（和聊天一样属于围观互动，不是对局操作），但有三条约束：
     * <ol>
     *   <li>必须已经在房间里（成员或观战者），否则无从谈起；</li>
     *   <li>目标必须是本局的一名玩家（{@code target} 是成员 key），不能凭空捏造；</li>
     *   <li>同一个人 {@link #TOMATO_COOLDOWN_MS} 内只能扔一次，防止刷屏。</li>
     * </ol>
     */
    private void onThrowTomato(WebSocketSession session, Map<String, Object> message) {
        synchronized (this) {
            String fromKey = sessionToKey.get(session.getId());
            Spectator fromSpectator = spectators.get(session.getId());
            if (fromKey == null && fromSpectator == null) {
                sendError(session, "请先加入房间");
                return;
            }
            // 投掷者的稳定标识：成员用自己的 key；观战者不在 members 里没有 key，
            // 借用 sessionId（限流只需要一个房间内唯一的值）。
            String throwerId = fromKey != null ? fromKey : "s:" + session.getId();
            long now = System.currentTimeMillis();
            Long last = lastTomatoAt.get(throwerId);
            if (last != null && now - last < TOMATO_COOLDOWN_MS) {
                sendError(session, "番茄扔得太快了，歇口气吧");
                return;
            }

            String targetKey = str(message.get("target"));
            Member target = targetKey == null ? null : members.get(targetKey);
            if (target == null) {
                sendError(session, "找不到要扔的玩家");
                return;
            }
            if (targetKey.equals(throwerId)) {
                sendError(session, "不能往自己脸上扔番茄");
                return;
            }
            lastTomatoAt.put(throwerId, now);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "tomato");
            event.put("from", throwerId);
            event.put("fromName", displayNameOf(throwerId));
            event.put("to", targetKey);
            event.put("toName", target.username);
            broadcast(event);
        }
    }

    /** 投掷者显示名：成员用用户名，观战者用其昵称或「观众」 */
    private String displayNameOf(String id) {
        if (id.startsWith("s:")) {
            Spectator spectator = spectators.get(id.substring(2));
            if (spectator == null) {
                return "观众";
            }
            return spectator.username == null || spectator.username.isEmpty() ? "观众" : spectator.username;
        }
        return usernameOf(id);
    }

    /**
     * 清掉已经离开房间的人的限流记录。
     *
     * 房间是长期存在的（默认房间永不销毁），而 {@link #lastTomatoAt} 的 key 包含
     * 游客 ID 和观战者 sessionId —— 这些都会不断产生新值。不定期清理就是内存泄漏。
     * 放在状态广播里顺带做，成本只是遍历一个很小的 map。
     */
    private void pruneTomatoThrottle() {
        lastTomatoAt.keySet().removeIf(id -> {
            if (id.startsWith("s:")) {
                return !spectators.containsKey(id.substring(2));
            }
            return !members.containsKey(id);
        });
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

    /** 房主 = 仍在线的「真人」成员中最早加入者；AI 与掉线者都不会成为房主 */
    private String hostKey() {
        for (Member member : members.values()) {
            if (member.connected && !member.ai) {
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

    /**
     * 当前还收得到消息的所有连接：在线成员 + 仍未关闭的观战者。
     *
     * 顺带把已经关掉的观战者从 map 里摘掉——它们再也收不到消息，而这里是唯一
     * 会定期遍历它们的地方。状态广播和瞬时事件（番茄）必须用同一套「谁还在线」
     * 的判断，所以抽出来共用，避免两处各写一遍后慢慢走样。
     */
    private List<WebSocketSession> reachableSessions() {
        List<WebSocketSession> sessions = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.connected && member.session != null) {
                sessions.add(member.session);
            }
        }
        for (Spectator spectator : new ArrayList<>(spectators.values())) {
            WebSocketSession session = spectator.session;
            if (session == null || !session.isOpen()) {
                spectators.remove(session == null ? "" : session.getId());
                continue;
            }
            sessions.add(session);
        }
        return sessions;
    }

    /** 向房间里的每个连接（在线成员 + 观战者）发送同一个瞬时事件 */
    private void broadcast(Map<String, Object> payload) {
        for (WebSocketSession session : reachableSessions()) {
            safeSend(session, payload);
        }
    }

    private void broadcastState() {
        pruneTomatoThrottle();
        // 观战者也在这里：任何进入网页的人都能看到房间里的对战
        for (WebSocketSession session : reachableSessions()) {
            sendState(session);
        }
    }

    private void sendState(WebSocketSession session) {
        String key = sessionToKey.get(session.getId());
        boolean spectating = key == null && spectators.containsKey(session.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "room_state");
        payload.put("roomCode", roomCode);
        payload.put("phase", roomPhase.name());
        payload.put("isHost", key != null && isHost(key));
        payload.put("you", key);
        // 自己的颜色，前端用来显示「你的颜色」并把自己和他人区分开
        payload.put("yourColor", colorOf(key));
        payload.put("yourColorName", colorNameOf(key));
        // 观战标记 + 观战人数，前端据此切换只读 UI
        payload.put("spectator", spectating);
        payload.put("spectatorCount", spectators.size());
        payload.put("chat", new ArrayList<>(chat));

        if (game == null) {
            payload.put("players", lobbyPlayers());
            payload.put("casinos", Collections.emptyList());
            payload.put("round", 0);
            payload.put("totalRounds", LasVegasGame.TOTAL_ROUNDS);
            // 观战者在大厅且未满员时可以选择入座
            payload.put("canJoin", spectating && members.size() < MAX_MEMBERS);
            payload.put("canStart", key != null && isHost(key)
                    && members.size() >= LasVegasGame.MIN_PLAYERS);
            payload.put("minPlayers", LasVegasGame.MIN_PLAYERS);
            payload.put("maxPlayers", LasVegasGame.MAX_PLAYERS);
            // 房主可添加电脑对手；前端据此显示按钮
            payload.put("canAddAi", key != null && isHost(key) && members.size() < MAX_MEMBERS);
            payload.put("canRemoveAi", key != null && isHost(key));
            payload.put("aiStyles", aiStyleOptions());
            payload.put("yourRoll", Collections.emptyList());
            payload.put("currentRoll", Collections.emptyList());
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
        payload.put("currentPlayerColor", colorOf(game.getCurrentPlayerId()));
        payload.put("lastPayout", payouts());

        boolean myTurn = key != null && key.equals(game.getCurrentPlayerId())
                && roomPhase == RoomPhase.PLAYING;
        payload.put("yourRoll", myTurn ? game.getLastRoll() : Collections.emptyList());
        // 当前投骰玩家投出的点数，对所有人（含观战者）公开 —— 这是观战的核心看点
        payload.put("currentRoll", game.getLastRoll());
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

    /** 可选电脑性格（名称 + 中文标签），供前端下拉框使用 */
    private List<Map<String, Object>> aiStyleOptions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LasVegasAi.Style style : LasVegasAi.Style.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", style.name());
            entry.put("label", style.getLabel());
            list.add(entry);
        }
        return list;
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
            entry.put("color", COLORS[member.colorIndex % COLORS.length]);
            entry.put("colorName", COLOR_NAMES[member.colorIndex % COLOR_NAMES.length]);
            entry.put("connected", member.connected);
            entry.put("isHost", member.key.equals(host));
            entry.put("ai", member.ai);
            entry.put("autopilot", member.autopilot);
            // 未开局时人手 8 颗；也下发这个字段，前端就能用同一段模板显示「剩余骰子」，
            // 不必为大厅和牌局各写一套
            entry.put("diceLeft", LasVegasGame.DICE_PER_PLAYER);
            if (member.ai) {
                entry.put("aiStyle", member.aiStyle.name());
                entry.put("aiStyleLabel", member.aiStyle.getLabel());
            }
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
            entry.put("color", colorOf(key));
            entry.put("colorName", colorNameOf(key));
            entry.put("connected", member != null && member.connected);
            entry.put("ai", member != null && member.ai);
            entry.put("autopilot", member != null && member.autopilot);
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
                d.put("color", colorOf(key));
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

    /** 玩家颜色（hex），未加入者返回 null */
    private String colorOf(String key) {
        Member member = members.get(key);
        return member == null ? null : COLORS[member.colorIndex % COLORS.length];
    }

    /** 玩家颜色中文名，未加入者返回 null */
    private String colorNameOf(String key) {
        Member member = members.get(key);
        return member == null ? null : COLOR_NAMES[member.colorIndex % COLOR_NAMES.length];
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
