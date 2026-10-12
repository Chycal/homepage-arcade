package com.homepage.lasvegas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepage.config.JwtUtil;
import com.homepage.lasvegas.model.LasVegasPlayerResult;
import com.homepage.lasvegas.repository.LasVegasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 房间层的电脑对手（AI）行为测试。
 *
 * 关注点不是 AI 的棋力（那由 {@link LasVegasAiTest} 覆盖），而是接线是否正确：
 * 房主权限、座位占用、AI 不会成为房主、AI 能自动走完自己的回合、
 * 以及 AI 不会把房间「焊死」。
 */
@DisplayName("拉斯维加斯房间 - 电脑对手")
class LasVegasRoomAiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LasVegasRepository repository;
    private LasVegasRoom room;
    private final AtomicInteger seq = new AtomicInteger();
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        repository = mock(LasVegasRepository.class);
        when(repository.createMatch(anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(1L);
        doAnswer(inv -> null).when(repository).addPlayerResult(anyLong(), any());
        jwtUtil = mock(JwtUtil.class);
        room = new LasVegasRoom("test", repository, jwtUtil);
        room.setAiStepDelayMs(0);
    }

    // ------------------------------------------------------------------
    // 测试用客户端
    // ------------------------------------------------------------------

    private class Client {
        final String id;
        final String guestId;
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        final WebSocketSession session;

        Client(String guestId) throws Exception {
            this.guestId = guestId;
            this.id = "session-" + guestId + "-" + seq.incrementAndGet();
            this.session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn(id);
            when(session.isOpen()).thenReturn(true);
            doAnswer(inv -> {
                messages.add(((TextMessage) inv.getArgument(0)).getPayload());
                return null;
            }).when(session).sendMessage(any(TextMessage.class));
        }

        /** 最近一次收到的 room_state */
        @SuppressWarnings("unchecked")
        Map<String, Object> state() throws Exception {
            synchronized (messages) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, Object> parsed = MAPPER.readValue(messages.get(i), Map.class);
                    if ("room_state".equals(parsed.get("type"))) {
                        return parsed;
                    }
                }
            }
            throw new AssertionError(guestId + " 尚未收到任何房间状态");
        }

        /** 最近一次收到的 error 消息文本，没有则返回 null */
        @SuppressWarnings("unchecked")
        String lastError() throws Exception {
            synchronized (messages) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, Object> parsed = MAPPER.readValue(messages.get(i), Map.class);
                    if ("error".equals(parsed.get("type"))) {
                        return String.valueOf(parsed.get("message"));
                    }
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players() throws Exception {
            return (List<Map<String, Object>>) state().get("players");
        }

        int aiCount() throws Exception {
            int count = 0;
            for (Map<String, Object> player : players()) {
                if (Boolean.TRUE.equals(player.get("ai"))) {
                    count++;
                }
            }
            return count;
        }
    }

    private Client join(String guestId) throws Exception {
        Client client = connect(guestId);
        send(client, "{\"type\":\"join\",\"guestId\":\"" + guestId
                + "\",\"username\":\"" + guestId + "\"}");
        return client;
    }

    /** 只建立连接，不发 join —— 便于测试自定义的 join 报文 */
    private Client connect(String guestId) throws Exception {
        Client client = new Client(guestId);
        room.handleConnect(client.session);
        return client;
    }

    private void send(Client client, String json) {
        room.handleMessage(client.session, json);
    }

    private void addAi(Client host) {
        send(host, "{\"type\":\"add_ai\"}");
    }

    // ==================================================================

    @Nested
    @DisplayName("大厅：添加 / 移除")
    class Lobby {

        @Test
        @DisplayName("房主可以添加电脑对手，AI 会出现在玩家列表里")
        void hostAddsAi() throws Exception {
            Client host = join("host");
            addAi(host);

            assertThat(host.aiCount()).isEqualTo(1);
            Map<String, Object> ai = host.players().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("ai")))
                    .findFirst().orElseThrow();
            assertThat(String.valueOf(ai.get("username"))).contains("电脑");
            assertThat(ai.get("key")).asString().startsWith("ai:");
            assertThat(ai.get("isHost")).isEqualTo(false);
            assertThat(host.state().get("canAddAi")).isEqualTo(true);
        }

        @Test
        @DisplayName("可以指定 AI 性格（solid / bold / wild）")
        void hostPicksAiStyle() throws Exception {
            Client host = join("host");
            send(host, "{\"type\":\"add_ai\",\"style\":\"wild\"}");
            send(host, "{\"type\":\"add_ai\",\"style\":\"bold\"}");
            send(host, "{\"type\":\"add_ai\",\"style\":\"不存在\"}");

            assertThat(host.aiCount()).isEqualTo(3);
            List<String> names = new ArrayList<>();
            for (Map<String, Object> player : host.players()) {
                if (Boolean.TRUE.equals(player.get("ai"))) {
                    names.add(String.valueOf(player.get("username")));
                }
            }
            assertThat(names.get(0)).contains("赌徒");
            assertThat(names.get(1)).contains("激进");
            assertThat(names.get(2)).contains("稳健"); // 无法识别 → 回落到稳健
            assertThat(host.state().get("aiStyles")).isNotNull();
        }

        @Test
        @DisplayName("非房主不能添加或移除电脑对手")
        void onlyHostCanManageAi() throws Exception {
            Client host = join("host");
            Client guest = join("guest");
            addAi(host);

            send(guest, "{\"type\":\"add_ai\"}");
            assertThat(guest.lastError()).contains("只有房主");

            String aiKey = host.players().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("ai")))
                    .findFirst().orElseThrow().get("key").toString();
            send(guest, "{\"type\":\"remove_ai\",\"key\":\"" + aiKey + "\"}");
            assertThat(guest.lastError()).contains("只有房主");
            assertThat(host.aiCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("房间满员时不能再添加电脑对手")
        void rejectsWhenFull() throws Exception {
            Client host = join("host");
            // 1 真人 + 4 电脑 = 5 人上限
            for (int i = 0; i < 4; i++) {
                addAi(host);
            }
            assertThat(host.aiCount()).isEqualTo(4);
            assertThat(host.state().get("canAddAi")).isEqualTo(false);

            addAi(host);
            assertThat(host.lastError()).contains("房间已满");
            assertThat(host.aiCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("移除电脑对手后编号会重排，不留空洞")
        void removeAiRenumbers() throws Exception {
            Client host = join("host");
            for (int i = 0; i < 3; i++) {
                addAi(host);
            }
            String secondKey = host.players().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("ai")))
                    .skip(1).findFirst().orElseThrow().get("key").toString();

            send(host, "{\"type\":\"remove_ai\",\"key\":\"" + secondKey + "\"}");
            assertThat(host.aiCount()).isEqualTo(2);

            List<String> names = new ArrayList<>();
            for (Map<String, Object> player : host.players()) {
                if (Boolean.TRUE.equals(player.get("ai"))) {
                    names.add(String.valueOf(player.get("username")));
                }
            }
            assertThat(names.get(0)).startsWith("电脑1");
            assertThat(names.get(1)).startsWith("电脑2");
        }

        @Test
        @DisplayName("移除不存在的目标会报错，不会误删真人")
        void removeRejectsNonAi() throws Exception {
            Client host = join("host");
            Client guest = join("guest");
            addAi(host);

            send(host, "{\"type\":\"remove_ai\",\"key\":\"不存在\"}");
            assertThat(host.lastError()).contains("找不到该电脑对手");

            // 试图用 remove_ai 删掉真人 → 必须被拒绝
            String guestKey = guest.state().get("you").toString();
            send(host, "{\"type\":\"remove_ai\",\"key\":\"" + guestKey + "\"}");
            assertThat(host.lastError()).contains("找不到该电脑对手");
            assertThat(host.players()).hasSize(3); // host + guest + 1 AI
        }

        @Test
        @DisplayName("电脑对手永远不会成为房主")
        void aiIsNeverHost() throws Exception {
            Client first = join("first");
            addAi(first);
            Client second = join("second");

            // first 离开后，房主应当移交给 second（真人），而不是 AI
            send(first, "{\"type\":\"leave\"}");
            assertThat(second.state().get("isHost")).isEqualTo(true);
            for (Map<String, Object> player : second.players()) {
                if (Boolean.TRUE.equals(player.get("ai"))) {
                    assertThat(player.get("isHost")).isEqualTo(false);
                }
            }
        }

        @Test
        @DisplayName("大厅里没有真人时会清空电脑对手，房间不会空转")
        void aiPrunedWhenNoHuman() throws Exception {
            Client host = join("host");
            for (int i = 0; i < 3; i++) {
                addAi(host);
            }
            assertThat(host.aiCount()).isEqualTo(3);

            send(host, "{\"type\":\"leave\"}");

            Client next = join("next");
            assertThat(next.players()).hasSize(1);
            assertThat(next.aiCount()).isZero();
        }
    }

    @Nested
    @DisplayName("对局：AI 自动行动")
    class Playing {

        @Test
        @DisplayName("AI 会自己掷骰、放骰，直到整局结束")
        void aiPlaysByItself() throws Exception {
            Client host = join("host");
            addAi(host);
            send(host, "{\"type\":\"start\"}");

            playUntilFinished(host);

            Map<String, Object> state = host.state();
            assertThat(state.get("phase")).isEqualTo("FINISHED");
            assertThat(state.get("winners")).isNotNull();
            assertThat(state.get("matchId")).isNotNull();

            // 两个座位都应当有收入或至少被正确结算（钱不会凭空多出来）
            int total = 0;
            for (Map<String, Object> player : host.players()) {
                total += ((Number) player.get("money")).intValue();
            }
            assertThat(total).isGreaterThan(0)
                    .isLessThanOrEqualTo(LasVegasBanknotes.TOTAL_VALUE);
        }

        @Test
        @DisplayName("对局开始后不能再增删电脑对手")
        void cannotManageAiDuringGame() throws Exception {
            Client host = join("host");
            addAi(host);
            send(host, "{\"type\":\"start\"}");

            addAi(host);
            assertThat(host.lastError()).contains("对局已开始");

            String aiKey = host.players().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("ai")))
                    .findFirst().orElseThrow().get("key").toString();
            send(host, "{\"type\":\"remove_ai\",\"key\":\"" + aiKey + "\"}");
            assertThat(host.lastError()).contains("对局已开始");
        }

        @Test
        @DisplayName("AI 回合不会卡住真人：轮到真人时照样能操作")
        void aiTurnDoesNotBlockHuman() throws Exception {
            Client host = join("host");
            addAi(host);
            send(host, "{\"type\":\"start\"}");

            // 一直推进到整局结束；过程中真人每次轮到自己都必须能拿到 canRoll/canChoose
            int humanTurns = 0;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                Map<String, Object> state = host.state();
                if ("FINISHED".equals(state.get("phase"))) {
                    break;
                }
                if (Boolean.TRUE.equals(state.get("canRoll"))) {
                    humanTurns++;
                    send(host, "{\"type\":\"roll\"}");
                } else if (Boolean.TRUE.equals(state.get("canChoose"))) {
                    send(host, "{\"type\":\"choose\",\"value\":" + greedyValue(state) + "}");
                } else {
                    Thread.sleep(5);
                }
            }
            assertThat(host.state().get("phase")).isEqualTo("FINISHED");
            // 4 轮里真人至少应当行动若干次，而不是被 AI 完全接管
            assertThat(humanTurns).isGreaterThan(0);
        }

        @Test
        @DisplayName("真人全部掉线时放弃本局，AI 不会把房间焊死")
        void abandonedGameDoesNotWedgeRoom() throws Exception {
            Client host = join("host");
            addAi(host);
            send(host, "{\"type\":\"start\"}");
            assertThat(host.state().get("phase")).isEqualTo("PLAYING");

            // 真人断开：此时场上已无真人，应当直接回到大厅并清掉 AI
            room.handleDisconnect(host.session);

            Client next = join("next");
            assertThat(next.state().get("phase")).isEqualTo("LOBBY");
            assertThat(next.aiCount()).isZero();
            assertThat(next.players()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("再来一局")
    class Restart {

        @Test
        @DisplayName("「再来一局」保留电脑对手，方便原班人马重开")
        void restartKeepsAi() throws Exception {
            Client host = join("host");
            addAi(host);
            send(host, "{\"type\":\"start\"}");
            playUntilFinished(host);

            send(host, "{\"type\":\"restart\"}");

            Map<String, Object> state = host.state();
            assertThat(state.get("phase")).isEqualTo("LOBBY");
            assertThat(host.aiCount()).isEqualTo(1);
            assertThat(state.get("canStart")).isEqualTo(true);

            // 保留的 AI 依然可以被正常移除
            String aiKey = host.players().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("ai")))
                    .findFirst().orElseThrow().get("key").toString();
            send(host, "{\"type\":\"remove_ai\",\"key\":\"" + aiKey + "\"}");
            assertThat(host.aiCount()).isZero();
        }

        @Test
        @DisplayName("AI 参与的战绩会被写入数据库，且 user_id 为 NULL")
        void aiResultsArePersisted() throws Exception {
            // 用一个「已登录」的真人，这样才能区分真人的 user_id 与 AI 的 NULL
            when(jwtUtil.getUserIdFromToken("tok")).thenReturn(42L);
            when(jwtUtil.extractUsername("tok")).thenReturn("真人甲");
            Client host = connect("host");
            send(host, "{\"type\":\"join\",\"token\":\"tok\"}");
            addAi(host);
            send(host, "{\"type\":\"start\"}");
            playUntilFinished(host);

            org.mockito.ArgumentCaptor<LasVegasPlayerResult> captor =
                    org.mockito.ArgumentCaptor.forClass(LasVegasPlayerResult.class);
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce())
                    .addPlayerResult(anyLong(), captor.capture());

            List<LasVegasPlayerResult> results = captor.getAllValues();
            assertThat(results).hasSize(2); // 1 真人 + 1 AI

            // 真人保留真实 user_id
            assertThat(results).filteredOn(r -> Long.valueOf(42L).equals(r.getUserId()))
                    .hasSize(1);
            // AI 不是真实用户，user_id 必须为 NULL，否则会污染真人战绩
            assertThat(results).filteredOn(r -> r.getUserId() == null).hasSize(1);
            assertThat(results).allSatisfy(r -> assertThat(r.getUsername()).isNotBlank());
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 用「选骰子最多的点数」把对局推到结束；同时驱动真人的回合 */
    private void playUntilFinished(Client human) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> state = human.state();
            if ("FINISHED".equals(state.get("phase"))) {
                return;
            }
            if (Boolean.TRUE.equals(state.get("canRoll"))) {
                send(human, "{\"type\":\"roll\"}");
            } else if (Boolean.TRUE.equals(state.get("canChoose"))) {
                send(human, "{\"type\":\"choose\",\"value\":" + greedyValue(state) + "}");
            } else {
                Thread.sleep(5);
            }
        }
        throw new AssertionError("对局未在 30 秒内结束，当前阶段="
                + human.state().get("phase"));
    }

    /** 从状态里取「骰子最多」的点数 */
    @SuppressWarnings("unchecked")
    private int greedyValue(Map<String, Object> state) {
        Map<String, Object> options = (Map<String, Object>) state.get("options");
        int best = -1;
        int bestCount = -1;
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            int value = Integer.parseInt(entry.getKey());
            int count = ((Number) entry.getValue()).intValue();
            if (count > bestCount || (count == bestCount && value < best)) {
                bestCount = count;
                best = value;
            }
        }
        assertThat(best).isGreaterThan(0);
        return best;
    }
}
