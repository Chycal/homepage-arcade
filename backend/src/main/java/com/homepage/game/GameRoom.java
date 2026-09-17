package com.homepage.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 单房间管理器：座位 + 观战 + 游戏状态 + 请求协商 + AI 对手
 */
@Service
public class GameRoom {

    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);
    private static final String AI_ID = "AI";
    private final ObjectMapper mapper = new ObjectMapper();

    // === 座位（sessionId 或 "AI"）===
    private volatile String blackSeat;
    private volatile String whiteSeat;

    // === AI 控制标志 ===
    private volatile boolean aiBlack;
    private volatile boolean aiWhite;

    // === 所有在线 session ===
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // === sessionId → userId（用于显示）===
    private final Map<String, String> sidToUid = new ConcurrentHashMap<>();

    // === 游戏 ===
    private GomokuGame game = new GomokuGame();
    private volatile String status = "waiting"; // waiting | playing | over

    // === 待确认请求 ===
    private volatile PendingRequest pendingReq;

    // === 评论区（最近 10 条）===
    private static final int MAX_COMMENTS = 10;
    private final LinkedList<Map<String, Object>> comments = new LinkedList<>();

    // === AI 线程池 ===
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gomoku-ai");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean aiThinking;

    // ==================== 消息入口 ====================

    public void handleMessage(WebSocketSession session, String payload) {
        try {
            Map<String, Object> msg = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.get("type");
            if (type == null) return;

            synchronized (this) {
                switch (type) {
                    case "join":        handleJoin(session, (String) msg.get("userId")); break;
                    case "sit":         handleSit(session, (String) msg.get("color")); break;
                    case "leave":       handleLeave(session); break;
                    case "move":        handleMove(session, (int) msg.get("row"), (int) msg.get("col")); break;
                    case "deploy_ai":   handleDeployAi(session, (String) msg.get("color")); break;
                    case "dismiss_ai":  handleDismissAi(session, (String) msg.get("color")); break;
                    case "undo_req":    handleUndoReq(session); break;
                    case "undo_resp":   handleUndoResp(session, (boolean) msg.get("agree")); break;
                    case "restart_req": handleRestartReq(session); break;
                    case "restart_resp":handleRestartResp(session, (boolean) msg.get("agree")); break;
                    case "draw_req":    handleDrawReq(session); break;
                    case "draw_resp":   handleDrawResp(session, (boolean) msg.get("agree")); break;
                    case "resign":      handleResign(session); break;
                    case "chat":        handleChat(session, (String) msg.get("text")); break;
                }
            }
        } catch (Exception e) {
            log.warn("消息处理异常: {}", e.getMessage());
            sendError(session, e.getMessage());
        }
    }

    public synchronized void handleDisconnect(WebSocketSession session) {
        String sid = session.getId();
        boolean wasPlayer = sid.equals(blackSeat) || sid.equals(whiteSeat);

        if (sid.equals(blackSeat)) blackSeat = null;
        if (sid.equals(whiteSeat)) whiteSeat = null;
        sessions.remove(sid);
        sidToUid.remove(sid);
        pendingReq = null;

        if (wasPlayer && "playing".equals(status)) {
            // 玩家断线 → 重置（AI 也清除）
            game = new GomokuGame();
            status = "waiting";
            clearAllAi();
        } else if (wasPlayer && "over".equals(status) && blackSeat == null && whiteSeat == null) {
            game = new GomokuGame();
            status = "waiting";
            clearAllAi();
        }
        broadcastRoomState();
    }

    // ==================== join / sit / leave ====================

    private void handleJoin(WebSocketSession session, String userId) {
        String sid = session.getId();
        sessions.put(sid, session);
        sidToUid.put(sid, userId != null ? userId : sid.substring(Math.max(0, sid.length() - 6)));
        broadcastRoomState();
    }

    /** 用户点击指定阵营坐下 */
    private void handleSit(WebSocketSession session, String color) {
        String sid = session.getId();
        if (sid.equals(blackSeat) || sid.equals(whiteSeat)) {
            sendError(session, "你已经入座了"); return;
        }

        boolean wantBlack = "black".equals(color);

        // 如果目标座位是 AI → 替换
        if (wantBlack && AI_ID.equals(blackSeat)) {
            aiBlack = false;
            blackSeat = sid;
            broadcastRoomState();
            return;
        }
        if (!wantBlack && AI_ID.equals(whiteSeat)) {
            aiWhite = false;
            whiteSeat = sid;
            broadcastRoomState();
            return;
        }

        if (wantBlack && blackSeat != null) { sendError(session, "黑方已有玩家"); return; }
        if (!wantBlack && whiteSeat != null) { sendError(session, "白方已有玩家"); return; }

        pendingReq = null;
        if (status.equals("over")) {
            game = new GomokuGame();
            status = "waiting";
            clearAllAi();
        }

        if (wantBlack) blackSeat = sid;
        else whiteSeat = sid;

        // 两人到齐 → 开局
        if (blackSeat != null && whiteSeat != null && status.equals("waiting")) {
            game = new GomokuGame();
            status = "playing";
            broadcastRoomState();
            scheduleAiMoveIfNeeded();
            return;
        }

        broadcastRoomState();
    }

    /** 离开座位（保留观战身份） */
    private void handleLeave(WebSocketSession session) {
        String sid = session.getId();

        if (sid.equals(blackSeat)) {
            blackSeat = null;
            if ("playing".equals(status)) {
                status = "over";
                broadcastGameOver(GomokuGame.WHITE);
                whiteSeat = null;
                clearAllAi();
            }
            pendingReq = null;
        } else if (sid.equals(whiteSeat)) {
            whiteSeat = null;
            if ("playing".equals(status)) {
                status = "over";
                broadcastGameOver(GomokuGame.BLACK);
                blackSeat = null;
                clearAllAi();
            }
            pendingReq = null;
        }

        broadcastRoomState();
    }

    // ==================== AI 部署 / 移除 ====================

    /** 坐席用户部署 AI 到对面空位 */
    private void handleDeployAi(WebSocketSession session, String color) {
        String sid = session.getId();
        boolean forBlack = "black".equals(color);

        // 权限：必须在对面座位
        if (forBlack) {
            if (!sid.equals(whiteSeat)) { sendError(session, "只有白方可以部署黑方AI"); return; }
            if (blackSeat != null && !AI_ID.equals(blackSeat)) { sendError(session, "黑方已有玩家"); return; }
            aiBlack = true;
            blackSeat = AI_ID;
            sidToUid.put(AI_ID, "AI");
        } else {
            if (!sid.equals(blackSeat)) { sendError(session, "只有黑方可以部署白方AI"); return; }
            if (whiteSeat != null && !AI_ID.equals(whiteSeat)) { sendError(session, "白方已有玩家"); return; }
            aiWhite = true;
            whiteSeat = AI_ID;
            sidToUid.put(AI_ID, "AI");
        }

        // 两人到齐 → 开局
        if (blackSeat != null && whiteSeat != null && status.equals("waiting")) {
            game = new GomokuGame();
            status = "playing";
        }

        broadcastRoomState();
        scheduleAiMoveIfNeeded();
    }

    /** 坐席用户移除对面 AI */
    private void handleDismissAi(WebSocketSession session, String color) {
        String sid = session.getId();
        boolean forBlack = "black".equals(color);

        if (forBlack) {
            if (!sid.equals(whiteSeat)) { sendError(session, "只有白方可以移除黑方AI"); return; }
            if (!aiBlack) { sendError(session, "黑方不是AI"); return; }
            aiBlack = false;
            blackSeat = null;
            sidToUid.remove(AI_ID);
        } else {
            if (!sid.equals(blackSeat)) { sendError(session, "只有黑方可以移除白方AI"); return; }
            if (!aiWhite) { sendError(session, "白方不是AI"); return; }
            aiWhite = false;
            whiteSeat = null;
            sidToUid.remove(AI_ID);
        }

        if ("playing".equals(status)) {
            // 移除 AI 时若在游戏中 → 重置换等待
            game = new GomokuGame();
            status = "waiting";
        }
        broadcastRoomState();
    }

    // ==================== 落子 ====================

    private void handleMove(WebSocketSession session, int row, int col) {
        if (!status.equals("playing")) { sendError(session, "游戏未开始"); return; }
        if (pendingReq != null) { sendError(session, "有待确认的请求"); return; }

        String sid = session.getId();
        int player = sid.equals(blackSeat) ? GomokuGame.BLACK : sid.equals(whiteSeat) ? GomokuGame.WHITE : -1;
        if (player == -1) { sendError(session, "你不在对局中"); return; }

        if (!game.place(row, col, player)) {
            sendError(session, "落子无效");
            return;
        }

        if (game.isOver()) {
            status = "over";
            int winner = game.getWinner();
            broadcastGameOver(winner);
            clearAllSeats();
        }

        broadcastRoomState();
        scheduleAiMoveIfNeeded();
    }

    // ==================== 悔棋 ====================

    private void handleUndoReq(WebSocketSession session) {
        if (!status.equals("playing")) { sendError(session, "游戏未开始"); return; }
        if (pendingReq != null) { sendError(session, "已有待确认请求"); return; }
        if (game.getMoveCount() < 2) { sendError(session, "步数不足，无法悔棋"); return; }

        String sid = session.getId();
        boolean isBlack = sid.equals(blackSeat);
        boolean isWhite = sid.equals(whiteSeat);
        if (!isBlack && !isWhite) { sendError(session, "你不在对局中"); return; }

        // 对手是 AI → 自动同意
        if (isOpponentAi(sid)) {
            game.undo();
            broadcastRoomState();
            scheduleAiMoveIfNeeded();
            return;
        }

        pendingReq = new PendingRequest("undo", sid);
        broadcastRoomState();
    }

    private void handleUndoResp(WebSocketSession session, boolean agree) {
        if (pendingReq == null || !"undo".equals(pendingReq.type)) { sendError(session, "没有悔棋请求"); return; }

        String sid = session.getId();
        String opponent = sid.equals(blackSeat) ? whiteSeat : sid.equals(whiteSeat) ? blackSeat : null;
        if (!pendingReq.from.equals(opponent)) { sendError(session, "不是你需响应的请求"); return; }

        if (agree) game.undo();
        pendingReq = null;
        broadcastRoomState();
    }

    // ==================== 重开 ====================

    private void handleRestartReq(WebSocketSession session) {
        if ((!status.equals("playing") && !status.equals("over")) || pendingReq != null) {
            sendError(session, "无法重开"); return;
        }
        String sid = session.getId();
        if (!sid.equals(blackSeat) && !sid.equals(whiteSeat)) { sendError(session, "你不在对局中"); return; }

        // 对手是 AI → 自动同意
        if (isOpponentAi(sid)) {
            game = new GomokuGame();
            status = "playing";
            broadcastRoomState();
            scheduleAiMoveIfNeeded();
            return;
        }

        pendingReq = new PendingRequest("restart", sid);
        broadcastRoomState();
    }

    private void handleRestartResp(WebSocketSession session, boolean agree) {
        if (pendingReq == null || !"restart".equals(pendingReq.type)) { sendError(session, "没有重开请求"); return; }

        String sid = session.getId();
        String opponent = sid.equals(blackSeat) ? whiteSeat : sid.equals(whiteSeat) ? blackSeat : null;
        if (!pendingReq.from.equals(opponent)) { sendError(session, "不是你需响应的请求"); return; }

        if (agree) {
            game = new GomokuGame();
            status = "playing";
        }
        pendingReq = null;
        broadcastRoomState();
    }

    // ==================== 求和 ====================

    private void handleDrawReq(WebSocketSession session) {
        if (!status.equals("playing")) { sendError(session, "游戏未开始"); return; }
        if (pendingReq != null) { sendError(session, "已有待确认请求"); return; }

        String sid = session.getId();
        if (!sid.equals(blackSeat) && !sid.equals(whiteSeat)) { sendError(session, "你不在对局中"); return; }

        // 对手是 AI → 自动同意
        if (isOpponentAi(sid)) {
            status = "over";
            broadcastGameOver(0);
            clearAllSeats();
            broadcastRoomState();
            return;
        }

        pendingReq = new PendingRequest("draw", sid);
        broadcastRoomState();
    }

    private void handleDrawResp(WebSocketSession session, boolean agree) {
        if (pendingReq == null || !"draw".equals(pendingReq.type)) { sendError(session, "没有求和请求"); return; }

        String sid = session.getId();
        String opponent = sid.equals(blackSeat) ? whiteSeat : sid.equals(whiteSeat) ? blackSeat : null;
        if (!pendingReq.from.equals(opponent)) { sendError(session, "不是你需响应的请求"); return; }

        if (agree) {
            status = "over";
            broadcastGameOver(0);
            clearAllSeats();
        }
        pendingReq = null;
        broadcastRoomState();
    }

    // ==================== 认输 ====================

    private void handleResign(WebSocketSession session) {
        if (!status.equals("playing")) { sendError(session, "游戏未开始"); return; }

        String sid = session.getId();
        int winner;
        if (sid.equals(blackSeat)) {
            winner = GomokuGame.WHITE;
        } else if (sid.equals(whiteSeat)) {
            winner = GomokuGame.BLACK;
        } else {
            sendError(session, "你不在对局中");
            return;
        }

        status = "over";
        pendingReq = null;
        broadcastGameOver(winner);
        clearAllSeats();
        broadcastRoomState();
    }

    // ==================== 评论 ====================

    private void handleChat(WebSocketSession session, String text) {
        if (text == null || text.trim().isEmpty()) return;
        String sid = session.getId();
        String uid = sidToUid.getOrDefault(sid, "?");
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("user", uid);
        comment.put("text", text.trim());
        comment.put("time", System.currentTimeMillis());
        comments.add(comment);
        if (comments.size() > MAX_COMMENTS) {
            comments.pollFirst();
        }
        broadcastRoomState();
    }

    // ==================== AI 行棋 ====================

    /** 如果轮到 AI 走，调度 AI 落子 */
    private void scheduleAiMoveIfNeeded() {
        if (!"playing".equals(status)) return;
        int cur = game.getCurrentPlayer();
        boolean isAiTurn = (cur == GomokuGame.BLACK && aiBlack) || (cur == GomokuGame.WHITE && aiWhite);
        if (!isAiTurn || aiThinking) return;

        aiThinking = true;
        aiExecutor.submit(() -> {
            try { Thread.sleep(600); } catch (InterruptedException e) { return; }
            synchronized (GameRoom.this) {
                executeAiMove();
            }
        });
    }

    /** 内部：AI 计算并落子 */
    private void executeAiMove() {
        aiThinking = false;
        if (!"playing".equals(status)) return;

        int cur = game.getCurrentPlayer();
        boolean isAiTurn = (cur == GomokuGame.BLACK && aiBlack) || (cur == GomokuGame.WHITE && aiWhite);
        if (!isAiTurn) return;

        int player = cur;
        int[][] boardCopy = new int[GomokuGame.SIZE][GomokuGame.SIZE];
        game.copyBoard(boardCopy);
        int[] move = GomokuAI.findBestMove(boardCopy, player);
        if (move == null) return;

        if (!game.place(move[0], move[1], player)) return;

        if (game.isOver()) {
            status = "over";
            broadcastGameOver(game.getWinner());
            clearAllSeats();
        }

        broadcastRoomState();
    }

    // ==================== 广播 ====================

    private void broadcastGameOver(int winner) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "game_over");
        msg.put("winner", winner);
        String text = toJson(msg);
        for (WebSocketSession s : sessions.values()) {
            safeSend(s, text);
        }
    }

    private void broadcastRoomState() {
        for (Map.Entry<String, WebSocketSession> e : sessions.entrySet()) {
            String text = buildRoomState(e.getKey());
            safeSend(e.getValue(), text);
        }
    }

    /** 构建发给某客户端的完整房间状态 */
    private String buildRoomState(String targetSid) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", "room_state");

        // 座位显示名
        state.put("black", blackSeat != null ? sidToUid.getOrDefault(blackSeat, "?") : null);
        state.put("white", whiteSeat != null ? sidToUid.getOrDefault(whiteSeat, "?") : null);

        // AI 标志
        state.put("aiBlack", aiBlack);
        state.put("aiWhite", aiWhite);

        // 本客户端的角色
        if (targetSid.equals(blackSeat) && !aiBlack) state.put("yourSeat", "black");
        else if (targetSid.equals(whiteSeat) && !aiWhite) state.put("yourSeat", "white");
        else state.put("yourSeat", "spectator");

        // 观战列表（排除 AI）
        List<String> specList = new ArrayList<>();
        for (String sid : sessions.keySet()) {
            if (!sid.equals(blackSeat) && !sid.equals(whiteSeat)) {
                specList.add(sidToUid.getOrDefault(sid, "?"));
            }
        }
        state.put("spectators", specList);
        state.put("spectatorCount", specList.size());

        // 游戏状态
        state.put("status", status);
        state.put("currentTurn", game.isOver() ? null : (game.getCurrentPlayer() == GomokuGame.BLACK ? "black" : "white"));
        state.put("moveCount", game.getMoveCount());

        // 棋盘
        int[][] raw = new int[GomokuGame.SIZE][GomokuGame.SIZE];
        game.copyBoard(raw);
        List<List<Integer>> board = new ArrayList<>(GomokuGame.SIZE);
        for (int[] row : raw) {
            List<Integer> r = new ArrayList<>(GomokuGame.SIZE);
            for (int v : row) r.add(v);
            board.add(r);
        }
        state.put("board", board);

        // 最后一步
        int[] lm = game.getLastMove();
        state.put("lastMove", lm != null ? Map.of("row", lm[0], "col", lm[1]) : null);

        // 赢家
        state.put("winner", game.isOver() ? (game.getWinner() == GomokuGame.BLACK ? "black" : game.getWinner() == GomokuGame.WHITE ? "white" : "draw") : null);

        // 评论
        state.put("comments", new ArrayList<>(comments));

        // 待确认请求
        if (pendingReq != null) {
            String fromUid = sidToUid.getOrDefault(pendingReq.from, "?");
            String fromSeat = pendingReq.from.equals(blackSeat) ? "black" : pendingReq.from.equals(whiteSeat) ? "white" : "?";
            state.put("pendingRequest", Map.of("type", pendingReq.type, "from", fromUid, "fromSeat", fromSeat));
        } else {
            state.put("pendingRequest", null);
        }

        return toJson(state);
    }

    // ==================== 工具方法 ====================

    /** 对方是 AI 吗？ */
    private boolean isOpponentAi(String mySid) {
        return (mySid.equals(blackSeat) && aiWhite) || (mySid.equals(whiteSeat) && aiBlack);
    }

    /** 清空双方座位 + AI */
    private void clearAllSeats() {
        blackSeat = null;
        whiteSeat = null;
        clearAllAi();
        pendingReq = null;
    }

    /** 清空 AI 标志 */
    private void clearAllAi() {
        aiBlack = false;
        aiWhite = false;
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", "error");
        err.put("message", message);
        safeSend(session, toJson(err));
    }

    private void safeSend(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(text));
                }
            }
        } catch (IOException e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 内部类 ====================

    private static class PendingRequest {
        String type;
        String from;

        PendingRequest(String type, String from) {
            this.type = type;
            this.from = from;
        }
    }
}
