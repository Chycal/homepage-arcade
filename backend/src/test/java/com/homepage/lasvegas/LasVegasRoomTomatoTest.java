package com.homepage.lasvegas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepage.config.JwtUtil;
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
 * 扔番茄表情的房间层测试。
 *
 * 番茄是纯动画事件，不进 {@code room_state}、不落库、不影响对局，
 * 因此这里只关心三件事：谁能扔、能扔给谁、以及限流是否生效。
 */
@DisplayName("拉斯维加斯房间 - 扔番茄")
class LasVegasRoomTomatoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LasVegasRoom room;
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void setUp() {
        LasVegasRepository repository = mock(LasVegasRepository.class);
        when(repository.createMatch(anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(1L);
        doAnswer(inv -> null).when(repository).addPlayerResult(anyLong(), any());
        room = new LasVegasRoom("test", repository, mock(JwtUtil.class));
        room.setAiStepDelayMs(0);
    }

    // ------------------------------------------------------------------
    // 测试用客户端
    // ------------------------------------------------------------------

    private class Client {
        final String guestId;
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        final WebSocketSession session;

        Client(String guestId) throws Exception {
            this.guestId = guestId;
            this.session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("session-" + guestId + "-" + seq.incrementAndGet());
            when(session.isOpen()).thenReturn(true);
            doAnswer(inv -> {
                messages.add(((TextMessage) inv.getArgument(0)).getPayload());
                return null;
            }).when(session).sendMessage(any(TextMessage.class));
        }

        /** 自己在这个房间里的 key（游客即 g:<guestId>） */
        String key() {
            return "g:" + guestId;
        }

        /** 收到过的全部 tomato 事件（按到达顺序） */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tomatoes() throws Exception {
            List<Map<String, Object>> found = new ArrayList<>();
            synchronized (messages) {
                for (String raw : messages) {
                    Map<String, Object> parsed = MAPPER.readValue(raw, Map.class);
                    if ("tomato".equals(parsed.get("type"))) {
                        found.add(parsed);
                    }
                }
            }
            return found;
        }

        /** 最近一次收到的 tomato 事件，没有则返回 null */
        Map<String, Object> lastTomato() throws Exception {
            List<Map<String, Object>> all = tomatoes();
            return all.isEmpty() ? null : all.get(all.size() - 1);
        }

        /** 最近一次收到的 error 文本，没有则返回 null */
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

        /**
         * 收到过的 error 条数。
         *
         * 「最后一次 error 是不是 null」不能用来判断某次操作是否成功 —— 之前的
         * error 会一直留在列表里被 {@link #lastError()} 找到。要断言「这次没报错」，
         * 只能比较条数有没有增加。
         */
        @SuppressWarnings("unchecked")
        int errorCount() throws Exception {
            int count = 0;
            synchronized (messages) {
                for (String raw : messages) {
                    Map<String, Object> parsed = MAPPER.readValue(raw, Map.class);
                    if ("error".equals(parsed.get("type"))) {
                        count++;
                    }
                }
            }
            return count;
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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players() throws Exception {
            return (List<Map<String, Object>>) state().get("players");
        }
    }

    private Client join(String guestId) throws Exception {
        Client client = new Client(guestId);
        room.handleConnect(client.session);
        send(client, "{\"type\":\"join\",\"guestId\":\"" + guestId
                + "\",\"username\":\"" + guestId + "\"}");
        return client;
    }

    private Client spectate(String guestId) throws Exception {
        Client client = new Client(guestId);
        room.handleConnect(client.session);
        send(client, "{\"type\":\"join\",\"guestId\":\"" + guestId
                + "\",\"username\":\"" + guestId + "\",\"spectate\":true}");
        return client;
    }

    private void send(Client client, String json) {
        room.handleMessage(client.session, json);
    }

    private void throwAt(Client thrower, String targetKey) {
        send(thrower, "{\"type\":\"throw_tomato\",\"target\":\"" + targetKey + "\"}");
    }

    /** 取大厅里第一个电脑对手的 key */
    private String aiKeyOf(Client client) throws Exception {
        for (Map<String, Object> player : client.players()) {
            if (Boolean.TRUE.equals(player.get("ai"))) {
                return String.valueOf(player.get("key"));
            }
        }
        throw new AssertionError("房间里没有电脑对手");
    }

    // ==================================================================

    @Nested
    @DisplayName("投掷与广播")
    class Throwing {

        @Test
        @DisplayName("扔向他人时，双方都会收到同一条 tomato 事件")
        void broadcastsToEveryone() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");

            throwAt(alice, bob.key());

            for (Client client : List.of(alice, bob)) {
                Map<String, Object> event = client.lastTomato();
                assertThat(event).as("应收到 tomato 事件").isNotNull();
                assertThat(event.get("from")).isEqualTo(alice.key());
                assertThat(event.get("to")).isEqualTo(bob.key());
                assertThat(event.get("fromName")).isEqualTo("alice");
                assertThat(event.get("toName")).isEqualTo("bob");
            }
        }

        @Test
        @DisplayName("观战者也能扔，并且能收到别人扔的番茄")
        void spectatorsCanThrowAndWatch() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");
            Client watcher = spectate("watcher");

            throwAt(watcher, bob.key());

            // 投掷者自己是观战者：from 用 s:<sessionId> 而不是成员 key
            Map<String, Object> seen = alice.lastTomato();
            assertThat(seen).isNotNull();
            assertThat(String.valueOf(seen.get("from"))).startsWith("s:");
            assertThat(seen.get("fromName")).isEqualTo("watcher");
            assertThat(seen.get("to")).isEqualTo(bob.key());
            // 观战者自己也收到了广播
            assertThat(watcher.lastTomato()).isNotNull();
        }

        @Test
        @DisplayName("可以扔电脑对手，事件里带上它的名字")
        void canTargetAi() throws Exception {
            Client alice = join("alice");
            send(alice, "{\"type\":\"add_ai\"}");
            String aiKey = aiKeyOf(alice);

            throwAt(alice, aiKey);

            Map<String, Object> event = alice.lastTomato();
            assertThat(event).isNotNull();
            assertThat(event.get("to")).isEqualTo(aiKey);
            assertThat(String.valueOf(event.get("toName"))).startsWith("电脑");
        }

        @Test
        @DisplayName("番茄只是事件，不改变对局状态")
        void doesNotMutateState() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");

            String before = String.valueOf(alice.state().get("phase"));
            throwAt(alice, bob.key());

            assertThat(alice.state().get("phase")).isEqualTo(before);
            assertThat(alice.players()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("非法投掷被拒绝")
    class Rejections {

        @Test
        @DisplayName("不在房间里的人不能扔")
        void outsiderCannotThrow() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");
            Client outsider = new Client("outsider");
            room.handleConnect(outsider.session);

            throwAt(outsider, bob.key());

            assertThat(outsider.lastError()).isEqualTo("请先加入房间");
            assertThat(alice.lastTomato()).isNull();
        }

        @Test
        @DisplayName("不能扔自己")
        void cannotTargetSelf() throws Exception {
            Client alice = join("alice");

            throwAt(alice, alice.key());

            assertThat(alice.lastError()).isEqualTo("不能往自己脸上扔番茄");
            assertThat(alice.lastTomato()).isNull();
        }

        @Test
        @DisplayName("目标不存在时报错，且不产生任何事件")
        void unknownTargetRejected() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");

            throwAt(alice, "g:nobody");

            assertThat(alice.lastError()).isEqualTo("找不到要扔的玩家");
            assertThat(bob.lastTomato()).isNull();
        }

        @Test
        @DisplayName("缺少 target 字段时报错")
        void missingTargetRejected() throws Exception {
            Client alice = join("alice");

            send(alice, "{\"type\":\"throw_tomato\"}");

            assertThat(alice.lastError()).isEqualTo("找不到要扔的玩家");
        }
    }

    @Nested
    @DisplayName("限流")
    class Throttle {

        @Test
        @DisplayName("同一人连续投掷会被拦下，但别人不受影响")
        void perThrowerCooldown() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");
            Client carol = join("carol");

            throwAt(alice, bob.key());
            assertThat(alice.lastTomato()).isNotNull();

            // 立刻再扔一次：被限流拦下，且没有新事件产生
            throwAt(alice, carol.key());
            assertThat(alice.lastError()).isEqualTo("番茄扔得太快了，歇口气吧");
            assertThat(alice.tomatoes()).hasSize(1);

            // 换个人扔，立刻就该成功 —— 限流是按人记的，不是全局冷却
            throwAt(bob, carol.key());
            assertThat(bob.lastError()).isNull();
            assertThat(bob.lastTomato()).isNotNull();
            assertThat(bob.lastTomato().get("to")).isEqualTo(carol.key());
        }

        @Test
        @DisplayName("被限流的那次不会把冷却时间重置，等待后即可再扔")
        void cooldownExpires() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");

            throwAt(alice, bob.key());
            assertThat(alice.tomatoes()).hasSize(1);
            assertThat(alice.errorCount()).isZero();

            throwAt(alice, bob.key());
            assertThat(alice.tomatoes()).as("限流期间不应新增事件").hasSize(1);
            assertThat(alice.errorCount()).isEqualTo(1);

            Thread.sleep(700);
            throwAt(alice, bob.key());
            assertThat(alice.tomatoes()).as("冷却结束后应能再扔").hasSize(2);
            assertThat(alice.errorCount()).as("冷却结束后不应再报错").isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // 剩余骰子下发
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("剩余骰子")
    class DiceLeft {

        @Test
        @DisplayName("大厅阶段每人都是满手的 8 颗")
        void lobbyReportsFullDice() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");
            send(alice, "{\"type\":\"add_ai\"}");

            assertThat(alice.players()).isNotEmpty();
            assertThat(alice.players()).allSatisfy(p ->
                    assertThat(((Number) p.get("diceLeft")).intValue())
                            .as("未开局时应显示满手骰子")
                            .isEqualTo(LasVegasGame.DICE_PER_PLAYER));
        }

        @Test
        @DisplayName("开局后每个人的骰子数都会下发，且不超过 8 颗")
        void playingReportsDice() throws Exception {
            Client alice = join("alice");
            Client bob = join("bob");
            send(alice, "{\"type\":\"start\"}");

            assertThat(alice.state().get("phase")).isEqualTo("PLAYING");
            assertThat(alice.players()).hasSize(2);
            assertThat(alice.players()).allSatisfy(p -> {
                int dice = ((Number) p.get("diceLeft")).intValue();
                assertThat(dice).isBetween(0, LasVegasGame.DICE_PER_PLAYER);
            });
        }
    }
}
