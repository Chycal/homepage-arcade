# 拉斯维加斯（Las Vegas）多人游戏实现大纲

> 目标：把 Rüdiger Dorn 的桌游《拉斯维加斯》实现为本站的多人联网小游戏。
> 本文分两部分：**A. 规则考据**（权威结论，可直接当验收标准）、**B. 工程实现大纲**（数据结构 / 状态机 / 协议 / 前端 / 测试 / 与现有代码的对接）。

---

## 0. 结论速览

| 项目 | 权威值 | 来源 |
|---|---|---|
| 人数 | **2–5 人**（基础版） | alea 官方规则书 |
| 骰子 | **40 颗 = 8 颗 × 5 色** | alea 官方规则书 |
| 赌场 | **6 个**，点数 1–6 | alea 官方规则书 |
| 钞票 | **54 张**（面额分布见 §1.3） | alea 官方规则书 |
| 每轮每场起始金额 | **≥ $50,000** | alea 官方规则书 |
| 轮数 | **4 轮** | alea 官方规则书 |
| 胜负 | 钱最多者胜；平手比**钞票张数**；再平手则并列 | alea 官方规则书 |
| 核心机制 | 掷全部剩余骰 → 选**一个点数** → 该点数**全部**骰子放到对应赌场 | alea 官方规则书 |
| 关键反直觉规则 | **同点数骰子数量相同的玩家，全部作废、颗粒无收** | alea 官方规则书 |

**⚠️ 三处常见误传（已核实为错）**，见 §9：轮数被误传为 3 轮、骰子被误传为 7 颗、人数被误传为 2–6 人。

---

# A. 规则考据

## 1. 配件与设置

### 1.1 赌场（6 个，点数 1–6）

| 点数 | 赌场名 |
|---|---|
| 1 | Golden Nugget |
| 2 | Caesar's Palace |
| 3 | The Mirage |
| 4 | Sahara |
| 5 | Luxor |
| 6 | Circus Circus |

赌场按点数 1→6 依次横排在桌面中央。

### 1.2 骰子

- 共 **40 颗**，**5 种颜色，每色 8 颗**。
- 每名玩家拿一色 8 颗。→ 基础版上限 5 人（这是 2–5 人的硬约束来源）。
- 基础版 8 颗骰子**完全同质**，没有大小之分。

### 1.3 钞票（54 张）

| 面额 | 张数 |
|---|---|
| $10,000 | 6 |
| $20,000 | 8 |
| $30,000 | 8 |
| $40,000 | 6 |
| $50,000 | 6 |
| $60,000 | 5 |
| $70,000 | 5 |
| $80,000 | 5 |
| $90,000 | 5 |
| **合计** | **54** |

校验：6+8+8+6+6 = 34；5×4 = 20；34+20 = **54** ✓

### 1.4 开局设置

1. 6 个赌场按点数顺序横排。
2. 54 张钞票洗混，面朝下成一叠放在赌场旁。
3. 给每个赌场发钞票，**直到该赌场总额 ≥ $50,000**，且所有面额必须可见。
   - 注意：是「加到 ≥ 50000 为止」，不是「恰好 50000」，所以实际值可能是 50k–99k 之间。
4. 每名玩家选一色拿 8 颗骰子。
5. **最年长的玩家**拿起始玩家卡。（线上实现改为随机或房主指定，见 §11。）

## 2. 回合流程

整局 **4 轮**。每轮中，每名玩家大约会有 4–5 次投骰机会。

**单个玩家的行动：**

1. **掷**：掷出自己手上**全部剩余骰子**（轮初 8 颗，越往后越少）。
2. **选**：从掷出的结果中，**必须且只能选一个点数**。
3. **放**：把该点数的**全部**骰子放到对应点数的赌场。
   - 不允许只放一部分！例：掷出 4 个 3 和 1 个 5，若选 3，则 4 个 3 必须**一起**放到 Mirage，不能只放 1–3 个。
   - 不要求点数是自己独有，赌场里已有谁的骰子都无所谓。
4. 顺时针轮到下一位。

**骰子用完后**：该玩家本轮剩余回合全部跳过（pass）。因此轮末可能出现只剩一人还有骰子、连续行动多次的情况。

**一轮结束**：当**最后一名玩家**把**最后一颗**骰子放到赌场后，立即结算。

## 3. 结算规则（核心，最易写错）

### 步骤 1：平手作废（先做！）

逐个赌场检查：**若有 ≥2 名玩家在该赌场的骰子数相同，则这些玩家全部把骰子收回，且从该赌场什么都拿不到。**

> 例 1：Anna 放了 5 颗到 Caesar's Palace，Benno 和 Carla 各放 3 颗，Denny 放 1 颗。
> → Benno 和 Carla 各收回自己的 3 颗（作废）。剩下 Anna(5) 和 Denny(1) 参与分配。
>
> 例 2：Anna 和 Carla 各放 2 颗到 Golden Nugget，Benno 和 Denny 各放 1 颗。
> → **四个人全部收回骰子，该赌场无人得分。**

⚠️ 实现要点：**平手判定是「按骰子数量分组」，且要迭代处理**。例 2 里 2/2/1/1 两组都平手，所以全灭。不是「只作废并列第一」。

### 步骤 2：按骰子数从多到少发钱

1. 骰子**最多**的玩家：收回该赌场自己全部骰子，拿走该赌场**面额最高**的钞票。
2. 其余玩家按骰子数**降序**依次拿剩余钞票（次高面额 → 再低……）。
3. **钞票不够分时**，排在后面的玩家空手而归，收骰子走人。
4. 每名玩家在每个赌场**最多只能拿 1 张钞票**。

### 步骤 3：剩余钞票

分配完后该赌场仍有剩余钞票 → 面朝下**放回钞票堆底部**（不是移出游戏，后续轮次还会被发出）。

其余 5 个赌场同样处理。玩家赢得的钱面朝下放在自己面前。

## 4. 下一轮

1. 给 6 个赌场补钞，使每个再次 **≥ $50,000**。
2. 起始玩家卡**传给左手边**的玩家。

## 5. 游戏结束

- **4 轮后**结束，各自数钱。
- **钱最多者胜。**
- **平手** → 比**钞票张数**（不是面额，是张数）多者胜。
- **仍平手** → **并列胜利**（多个赢家）。

## 6. 官方变体：中立骰（2–4 人）

基础版只有 5 色骰子，人数少时赌场会太空，官方给出补足机制：

每轮开始时，除自己 8 颗彩色骰外，额外获得**中立色（建议白色）**骰子：

| 人数 | 每人额外中立骰 | 每人总骰数 |
|---|---|---|
| 2 | 4 | 12 |
| 3 或 4 | 2 | 10 |

- 3 人局时会有 **2 颗多余白骰**，由**起始玩家在轮初掷出并按规则放好**。
- 行动时，中立骰与自己的骰子**一起掷**，选点数时**可以选中立骰的点数**，放置规则相同。
- 结算时，中立骰视作**一个虚拟玩家**参与比较；虚拟玩家赢到的钞票**放回钞票堆底部**（谁也不得）。

> 例：Carla 掷出 2 个自己颜色的 1、1 个白色 1、1 个自己的 5、1 个中立 6。
> → 她可选「放 3 个 1」（2 彩 + 1 白）、或「放 1 个 5」、或「放 1 个 6」。

## 7. 官方规则书原文要点（英文原句摘录，供核对）

> "The winner is the player with the most money after **four** rounds."
> "**40** dice (**8** in each player colour)"
> "**54** banknotes (5 each of $60,000, $70,000, $80,000 and $90,000; 6 each of $10,000, $40,000 and $50,000; 8 each of $20,000 and $30,000)"
> "Keep adding banknotes to a casino until the total value is at least **$50,000**."
> "you must choose **exactly one** of the numbers rolled and add **all** of those dice to the matching-numbered casino"
> "check each casino to see if two or more players have the **same number of dice** there. Wherever this is the case, each of the affected players return all of their affected dice back to their supplies."
> "Should there not be enough banknotes for everyone, then the remaining players miss out."
> "In the event of a tie, then the winner is the player with the most **banknotes**."

## 8. 扩展内容（**不属于基础版**，若要实现需单独标注）

以下机制来自后续扩展（Las Vegas Royale / Boulevard 等），**不要混入基础规则**：

| 模块 | 机制摘要 |
|---|---|
| **Minimum amount** | 每场起始金额随人数变化：2人 $30k / 3人 $40k / 4人 $50k / 5人 $60k / 6人 $70k |
| **$100,000 钞票** | 加入 5 张 $100,000 混入钞票堆 |
| **Biggies（大骰）** | 每人用 1 颗大骰替换普通骰，结算时**算 2 颗** |
| **Kickers（踢骰）** | 紫色骰，放下时立即把该赌场**任意 1 颗**骰子踢回原主，踢骰本身**不留在赌场**；空赌场无效 |
| **Rainbow Cards** | 彩虹钞票，设置时按 $40,000 计；结算前弃掉换成新钞 |
| **Slot Machine（老虎机）** | 第 7 个赌场：可放任意点数，但**同点数不能重复**（含自己）；平手时比**点数总和**，再比最大单颗 |
| 2–6 人适配 | Yucata 实现对 3–6 人给 2 颗中立骰 |

## 9. 歧义与不确定点（重要）

| # | 争议 | 结论 | 依据 |
|---|---|---|---|
| 1 | **3 轮还是 4 轮？** | **4 轮**。有中文评测写成 3 轮，属误传 | alea 官方规则书原文 "four rounds"；UltraBoardGames 与 Lehi 摘要均同 |
| 2 | **7 颗还是 8 颗骰？** | **8 颗**。「6 小 + 1 大」是扩展 Biggies 的配置 | alea 官方规则书 "8 dice in each player colour" |
| 3 | **2–5 人还是 2–6 人？** | 基础版 **2–5**（40 颗 ÷ 8 = 5 色）。「2–6」出现在 Yucata 扩展适配与 2026 新版宣传 | 骰子总数算术上限定为 5；官方变体节标题为 "Variant (2 to 4 players)" |
| 4 | 起始玩家如何定？ | 官方是「最年长玩家」，线上应改为随机/房主指定 | 年龄规则不适合线上 |
| 5 | 平手作废是否迭代？ | 是。2/2/1/1 导致全员作废（官方例 2 明确） | alea 官方规则书例 2 |
| 6 | 3 人局多余的 2 颗白骰 | 由起始玩家掷并放好 | alea 官方规则书变体节 |
| 7 | 2026 新版是否改规则？ | **未找到可靠的一手规则书**，仅见宣传稿。本大纲以 2012 alea 版为准 | 见 §10 来源 |

## 10. 来源清单

**一手来源（权威，本大纲以此为准）**
- alea 官方规则书全文（2011 Rüdiger Dorn / 2012 Ravensburger Spieleverlag，编号 230512）— 经 [Lehi-Innovation 规则库](https://lehi-innovation.github.io/boardgame-rules/extracted/las-vegas-rules.txt) 提取的 OCR 全文。**这是本大纲所有数值的第一依据。**
- [UltraBoardGames 官方规则复刻](https://cdn.ultraboardgames.com/las-vegas/game-rules.php) — 含配件清单、逐条流程、2–4 人变体，与规则书逐句对应。

**一手实现（官方授权线上版，可交叉验证）**
- [Yucata 官方实现规则页](https://www.yucata.de/es/Rules/LasVegas) — 含扩展模块（Biggies/Kickers/Rainbow/Slot Machine）与实现层规则澄清。

**二手来源（仅用于交叉验证，**与权威值冲突处已按 §9 标注**）**
- [Lehi-Innovation 规则摘要](https://lehi-innovation.github.io/boardgame-rules/rules/las-vegas/) — 已自标 "Verified"，与规则书一致。
- [Gamereactor 中文评测](https://www.gamereactor.cn/las-vegas-the-board-game-1374683/) — **轮数(3)、骰数(7)、人数(2–6) 三处与官方规则书冲突，判为误传**。
- [Mobius（alea 日本代理）产品页](http://mobius-games.co.jp/Archive/alea/LasVegas/LasVegas.html) — 标注 2~5 人 / 30 分，支持基础版 2–5 人结论。
- [BoardGameGeek 页面](https://boardgamegeek.com/boardgame/117959/las-vegas) — 抓取被 403 拦截，仅作条目定位。
- [集石讨论帖](https://www.gstonegames.com/bbs/discuss-6-74152-1.html) — 玩家口碑参考（无规则数值）。

---

# B. 工程实现大纲

## 11. 与现有代码的对接（关键改造点）

现有五子棋架构**假定恰好 2 人**，拉斯维加斯需要 2–5 人，这是最大的改造点。

### 11.1 现状（需改造）

`backend/src/main/java/com/homepage/game/GameRoom.java`：

```java
private volatile String blackSeat;   // ← 写死两个座位
private volatile String whiteSeat;
private volatile boolean aiBlack;
private volatile boolean aiWhite;
private GomokuGame game = new GomokuGame();
private volatile String status = "waiting";  // waiting | playing | over
```

问题：
- 座位是**两个独立字段**，不是集合 → 无法容纳 3–5 人。
- `GomokuGame` 与房间强耦合 → 换游戏要动 `GameRoom` 主体。
- `handleSit` 里 `"black"/"white"` 硬编码。

### 11.2 建议的重构方向

**方案（推荐）：抽出游戏无关的「座位/房间」层，游戏规则放进独立引擎类。**

```
com.homepage.game
├── GameRoom.java              ← 改为通用：座位表 + 观战 + 广播（不含具体规则）
├── GameWebSocketHandler.java  ← 不动
├── gomoku/
│   ├── GomokuGame.java        ← 从 game/ 移入（逻辑不变）
│   └── GomokuAI.java
└── lasvegas/
    ├── LasVegasGame.java      ← 新增：纯规则引擎（无 Spring 依赖，可单测）
    ├── LasVegasDice.java      ← 新增：骰子池/投掷
    └── LasVegasTable.java     ← 新增：赌场 + 钞票堆状态
```

**通用座位模型：**

```java
// GameRoom 内的座位表，替代 blackSeat/whiteSeat
private final List<Seat> seats = new ArrayList<>();   // 2..5 个
private static class Seat {
    String sid;          // sessionId 或 "AI"
    String userId;       // 显示名
    boolean ready;
    boolean isAi;
}
private volatile int maxSeats = 5;      // 按游戏类型设定：五子棋 2，拉斯维加斯 5
private volatile int minSeats = 2;
```

**游戏引擎统一接口**（让 `GameRoom` 不关心具体规则）：

```java
public interface GameEngine {
    int minPlayers();
    int maxPlayers();
    void start(List<String> playerIds);        // 按座位顺序
    boolean applyAction(String playerId, Map<String,Object> action);
    boolean isOver();
    Map<String,Object> snapshot(String viewerId);   // 给某客户端的状态视图
    String currentTurnPlayerId();
}
```

> 若不想一次重构到位，**折中方案**：新建 `LasVegasRoom` + `LasVegasWebSocketHandler`，注册到 `/ws/lasvegas`，与五子棋完全并行、互不影响。改造风险最低，但会有代码重复。**建议先走折中方案快速上线，再考虑抽 `GameEngine`。**

### 11.3 必须遵守的项目规则

> 来源：`.codebuddy/rules/新增功能时规则.mdc`

新增功能**必须在「主页功能栏」和「顶部栏」都加跳转按钮**：
- `frontend/src/App.vue` 的 `.nav-links`（约第 12–13 行，`<router-link to="/gomoku">♟ 五子棋</router-link>` 旁）
- `frontend/src/views/Home.vue` 的 `.btn-game` 区块（约第 11–13 行）

> 来源：`.codebuddy/rules/编写项目代码和编译项目代码时规则.mdc`

编译 Java 前**必须先停掉正在运行的后端**，防止爆内存：
```bash
systemctl stop homepage-arcade
cd backend && mvn package
systemctl start homepage-arcade
```

## 12. 数据结构设计

### 12.1 游戏状态（服务端权威）

```java
public class LasVegasGame {
    // 轮次
    private int round = 0;                 // 1..4
    private static final int TOTAL_ROUNDS = 4;

    // 玩家（按座位顺序 = 顺时针行动顺序）
    private final List<Player> players = new ArrayList<>();   // 2..5

    // 赌场：索引 1..6 对应点数
    private final Casino[] casinos = new Casino[7];           // [0] 不用

    // 钞票堆（有序，从顶部抽）
    private final Deque<Integer> banknoteDeck = new ArrayDeque<>();  // 存面额

    // 起始玩家下标
    private int startPlayerIdx = 0;

    // 当前行动者下标
    private int currentIdx = 0;

    // 阶段
    private Phase phase = Phase.PLACING;   // PLACING | PAYOUT | ROUND_END | GAME_OVER
}

public class Player {
    private String id;                     // userId
    private int color;                     // 0..4 颜色索引
    private List<Integer> handDice;        // 手上剩余骰子（基础版：8 个元素，值 0 表示未掷）
    private List<Integer> neutralDice;     // 中立骰（2–4 人变体）
    private int money;                     // 累计金额
    private int banknoteCount;             // 钞票张数（用于平手裁决）
}

public class Casino {
    private int dieValue;                  // 1..6
    private List<Integer> banknotes;       // 当前可用钞票面额（有序或可排序）
    private Map<String, Integer> diceCount; // playerId -> 该玩家在此赌场的骰子数
    // 中立骰虚拟玩家用保留 id "NEUTRAL"
}
```

### 12.2 关键不变量

- `banknoteDeck.size() + Σ(casino.banknotes.size()) + 玩家手中钞票数 == 54`（**全局守恒，强烈建议写成断言/测试**）
- 每轮开始时：每个 `casino.banknotes` 总和 `>= 50000`
- `Σ(玩家骰子) + Σ(赌场骰子) + 中立骰 == 40`（基础版）
- 每个玩家在每个赌场最多拿 1 张钞票

## 13. 服务端状态机

```
WAITING ──(人数达标 + 房主开始)──> ROUND_START
                                      │
                                      ▼
ROUND_START ──(发钞至每场≥$50k；重置骰子)──> PLACING
                                      │
                          ┌───────────┘
                          ▼
PLACING ──(当前玩家 roll → choose → place)──> 下一位
   │                                            │
   │  全部玩家骰子用尽                            │
   ▼                                            │
PAYOUT ──(① 平手作废 ② 降序发钞 ③ 余钞回底)──────┘
   │
   │  round < 4
   ▼
ROUND_START（round++，起始玩家左移）
   │
   │  round == 4
   ▼
GAME_OVER ──> 结算排名（钱 → 张数 → 并列）
```

### 13.1 每步的校验（服务端必须拒绝非法操作）

| 操作 | 校验 |
|---|---|
| `roll` | 阶段为 PLACING；调用者是当前玩家；手上有骰 |
| `choose` | 点数 ∈ 1..6；该点数在本次投掷结果中**存在** |
| `place` | 放置的是**该点数的全部**骰子（不可部分）；目标赌场 = 该点数 |
| 任何操作 | 非当前玩家 → 拒绝；阶段不符 → 拒绝 |

> **投骰必须在服务端完成**（用 `SecureRandom` 或 `ThreadLocalRandom`），客户端只做展示。否则可被篡改。

### 13.2 平手作废算法（伪代码）

```
for each casino:
    // 按骰子数分组
    Map<count, List<playerId>> byCount
    for (pid, cnt) in casino.diceCount:
        if cnt > 0: byCount[cnt].add(pid)

    // 任何 count 组里人数 >= 2 → 该组全部作废
    Set<playerId> voided = {}
    for (cnt, pids) in byCount:
        if pids.size() >= 2: voided.addAll(pids)

    // 作废者收回骰子，不参与分配
    for pid in voided:
        casino.diceCount.remove(pid)     // 骰子回到玩家 supply
        // 该玩家从该赌场出局，不拿钱

    // 剩余按骰子数降序
    List<(pid, cnt)> ranked = casino.diceCount 按 cnt 降序
    List<Integer> notes = casino.banknotes 按面额降序

    for i, (pid, cnt) in ranked:
        if i >= notes.size(): break       // 钞票不够，后面的人空手
        award(pid, notes[i])              // 每人最多 1 张

    // 剩余钞票放回堆底
    把 notes[ranked.size()..] 面朝下压入 banknoteDeck 底部
```

⚠️ 注意 `ranked` 的并列处理：作废步骤已保证**剩余玩家的骰子数互不相同**，所以降序排列无歧义。

## 14. WebSocket 协议设计

> **本节已按实现更新。** 下面的报文是 `LasVegasRoom` 实际收发的格式（早期草案里的
> `sit` / `ready` / `your_roll` / `placed` / `payout` 等独立消息类型并未采用——
> 服务端统一推送一份完整的 `room_state`，前端按字段渲染，避免多消息类型不同步）。

### 14.1 客户端 → 服务端

```json
{ "type": "join",      "token": "<JWT，可空>", "guestId": "g…", "username": "…", "spectate": false }
{ "type": "leave" }
{ "type": "start" }
{ "type": "roll" }
{ "type": "choose",    "value": 3 }
{ "type": "restart" }
{ "type": "add_ai",    "style": "SOLID|BOLD|WILD" }
{ "type": "remove_ai", "key": "ai:1" }
{ "type": "chat",      "text": "…" }
{ "type": "throw_tomato", "target": "u:2" }
{ "type": "state" }
```

`join` 的 `spectate: true` 表示「我只观战，不要座位」。**不带该标记也可能被降级为观战者**：
对局已开始、或大厅已满员时，服务端不再报错，而是把该连接登记为观战者。

### 14.2 服务端 → 客户端

只有三种下行消息：`room_state`（全量状态）、`tomato`（瞬时表情）与 `error`。

```json
{ "type": "room_state",
  "roomCode": "default", "phase": "LOBBY|PLAYING|FINISHED",
  "you": "u:2", "isHost": false,
  "yourColor": "#1e88e5", "yourColorName": "蓝",
  "spectator": false, "spectatorCount": 0,
  "canJoin": false,
  "players": [ { "key":"u:2", "username":"…", "seat":0,
                 "color":"#e53935", "colorName":"红",
                 "connected":true, "isHost":true, "ai":false, "autopilot":false,
                 "diceLeft":8, "money":120000, "banknotes":3 } ],
  "casinos": [ { "value":1, "banknotes":[30000,20000], "total":50000,
                 "dice":[ { "seat":0, "username":"…", "color":"#e53935", "count":4 } ] } ],
  "round": 2, "totalRounds": 4,
  "currentPlayerKey": "u:2", "currentPlayerName": "…", "currentPlayerColor": "#e53935",
  "currentRoll": [3,3,3,5,1,2,6,3],
  "yourRoll": [], "options": {}, "canRoll": false, "canChoose": false,
  "lastPayout": [ { "value":1, "voided":[], "awards":[{"seat":0,"note":40000}], "leftover":[10000] } ],
  "chat": [ { "username":"…", "color":"#e53935", "colorName":"红",
              "spectator":false, "text":"…" } ],
  "deckSize": 41, "deckTotal": 1930000,
  "winners": [ { "key":"u:2", "username":"…", "money":310000, "banknotes":9 } ],
  "matchId": 31 }

{ "type": "tomato", "from": "u:2", "fromName": "…", "to": "u:3", "toName": "…" }

{ "type": "error", "message": "请先加入房间" }
```

**可见性边界（核心设计）**——同一份 `room_state`，按接收者裁剪：

| 字段 | 参赛者（轮到自己） | 参赛者（别人回合） | 观战者 |
| --- | --- | --- | --- |
| `currentRoll` 当前投骰点数 | ✅ | ✅ | ✅ |
| `yourRoll` 自己掷出的点数 | ✅ | 空 | 空 |
| `options` 可选点数 | ✅ | 空 | 空 |
| `canRoll` / `canChoose` | ✅ | false | false |
| `yourColor` / `yourColorName` | ✅ | ✅ | null |
| `canJoin`（大厅未满时可入座） | false | false | ✅ |

> 关键点：**`currentRoll` 对所有人公开**（含观战者），这是观战的核心看点；
> 而 `yourRoll` / `options` / `canRoll` / `canChoose` 仍然只给当前玩家，避免观战者代打。
> 两者内容在投骰者本人身上是相同的，区别只在于「谁能拿到」。

### 14.3 观战与权限边界

- 观战者存在独立的 `spectators`（`sessionId -> Spectator`）里，**不进 `members`、不进 `sessionToKey`**。
  于是 `requirePlayingTurn` / `isHost` / `onStart` / `onRoll` / `onChoose` / `onAddAi` / `onRemoveAi`
  一律查不到它，自然回落到 `"请先加入房间"`——权限边界由数据结构保证，不靠逐处 if 判断。
- 任何进入网页的人都能收到 `room_state`：未 `join` 的连接在对局中会自动成为观战者，
  满足「所有进入网页的人都能看到房间里的对战」。
- 观战者**可以聊天**（聊天是围观互动，不是对局操作），消息带 `spectator: true` 与名字「观众」。
- 大厅未满员时，观战者可以点「加入这局」正式入座（`canJoin`）。

### 14.4 离开、掉线与托管

- **离开（`leave`）**：离开者立刻收到一份「自己不在房间里」的状态，页面不再显示自己还在房间
  （修掉了「离开后页面不刷新」的 bug）；随后被转为观战者，可以继续围观。
- **对局中离开/掉线**：座位保留（便于重连），但立即标记 `autopilot: true` 交给 AI 托管。
  否则该座位没人能操作，剩下的玩家会被**永久卡住**。
- **重连**：本人回来时 `autopilot` 复位，收回控制权。同一账号开第二个标签页时，
  旧连接被降级为观战者，而不是留下一个收不到消息的幽灵连接。
- **回收**：大厅里若已无真人，AI 一并清掉；对局中若真人全部掉线，直接放弃本局回到大厅，
  避免死局永久占位（注意判断「有无真人」时必须排除 AI 与托管者，它们恒为「在线」）。

### 14.5 扔番茄（表情互动）

`throw_tomato` 是**纯表情**，只驱动一段动画，与对局状态完全解耦：

- **不进 `room_state`、不落库、不改变任何对局字段**。番茄是瞬时事件，收到就播，
  错过了也不影响任何人的局面。因此它不会污染状态广播，也不需要「已读」之类的补偿逻辑。
- **广播范围**：所有成员 + 所有观战者。前端靠事件里的 `from` / `to` 两个 key
  去查 `[data-avatar="…"]` 拿屏幕坐标，实时算一条抛物线；任一端不在屏幕上就静默忽略。
- **谁能扔**：成员与观战者都可以。观战者不在 `members` 里没有 key，用 `s:<sessionId>`
  作为稳定标识；`fromName` 取观战者昵称，缺省为「观众」。
- **能扔给谁**：只能是**房间里的成员**（含 AI）。目标是观战者时按「找不到要扔的玩家」拒绝——
  观战者的头像不在两侧栏里，扔过去也没有落点。
- **限流**：服务端按投掷者记 `TOMATO_COOLDOWN_MS = 600`，冷却内的投掷直接回
  `"番茄扔得太快了，歇口气吧"`，且**不刷新冷却时间**（否则连点就能一直续上）。
  限流表 `lastTomatoAt` 在每次 `broadcastState()` 时顺带 `pruneTomatoThrottle()`，
  清掉已离开的成员与观战者——房间是长驻的，不清理就是内存泄漏。

## 15. 前端设计

### 15.1 新增文件

```
frontend/src/views/LasVegas.vue          ← 主视图
frontend/src/views/LasVegasRulesPage.vue ← 独立规则页（路由 /las-vegas/rules）
frontend/src/components/LasVegasRules.vue← 规则内容，被上两者复用
frontend/src/utils/lasvegas.js           ← 赌场名、骰子颜色、金额格式化（主视图与规则页共用）
```

### 15.2 路由注册

`frontend/src/router/index.js`：

```js
{
  path: '/las-vegas',
  name: 'LasVegas',
  component: () => import('@/views/LasVegas.vue'),
  meta: { title: '拉斯维加斯 - chycal' }
}
```

### 15.3 导航按钮（项目规则强制，两处都要加）

`App.vue`：
```html
<router-link to="/las-vegas" @click="menuOpen = false">🎲 拉斯维加斯</router-link>
```

`Home.vue`：
```html
<router-link to="/las-vegas" class="btn-contact btn-game">🎲 拉斯维加斯</router-link>
```

### 15.4 界面区块（已按实现更新）

| 区块 | 内容 |
|---|---|
| **赌场区** | 6 个赌场卡（点数 + **钞票面额列表（主信息，14px 加粗）** + 下方小字灰色「合计 $…」+ 已放骰子按玩家颜色分组显示） |
| **我的骰子区** | 剩余骰子；投骰后高亮可选点数，显示「放 N 个 3」按钮 |
| **头像两侧栏** | 每个玩家的头像按**座位奇偶**分列屏幕左右两侧（座位固定 → 同一人整局不换边）。头像内角显示剩余骰子数与 🍅 按钮；宽屏固定在视口两侧，窄屏降级为可横向滚动的头像带 |
| **玩家面板** | 每人的颜色、剩余骰数、已赢金额、钞票张数 |
| **颜色图例卡** | 自己的颜色（`yourColor`）+ 全场每个人的颜色圆点与「我 / 电脑 / 托管」标记；观战者看到的是「👀 你正在观战」 |
| **当前投骰提示** | 「**某某** 掷出了：⚀⚂⚂」——按该玩家颜色着色，**所有人（含观战者）可见** |
| **轮次/回合提示** | 「第 2 / 4 轮」「轮到 chycal」（名字按该玩家颜色着色） |
| **观战面板** | 观战人数；观战者看到只读的全场金额/钞票/剩余骰子一览 |
| **聊天** | 自动跟随最新消息 + 上滑时出现「有新消息 ↓」跳转按钮；观众发言带「观众」标记 |
| **扔番茄** | 点头像角上的 🍅 向该玩家扔一颗番茄，动画从自己头像抛物线飞到对方头像并爆出 💥；纯表情，不影响对局（见 §14.5） |

### 15.5 交互要点

- 投骰后，**相同点数的骰子自动成组**，每组一个按钮；点按钮即放置全部。
- 无选择时（只剩 1 颗骰，或剩余骰子全同点数）→ 可自动执行（Yucata 就是这么做的），但**需给玩家明确反馈**，避免"没点按钮就跳过了"的困惑。
- 结算阶段用动画/高亮展示：哪些骰子作废（变灰 + 打叉）、谁拿了哪张钞票。
- 颜色需对色盲友好：5 种颜色之外**再给每色一个图案/文字标识**。

## 16. 测试大纲

沿用现有约定：JUnit 5 + AssertJ，`@Nested` + `@DisplayName`，纯引擎单测不依赖 Spring（参考 `GomokuGameTest`）。

> ⚠️ Boot 2.7.18 的 AssertJ **没有** `assertThat(int[]).allMatch(lambda)`，用 `assertThat(Arrays.stream(arr).allMatch(...)).isTrue()`。

### 16.1 引擎单测清单（`LasVegasGameTest`）

| 组 | 用例 |
|---|---|
| 设置 | 2/3/4/5 人局各自配件正确；每场 ≥$50,000；钞票总数 54 |
| 投骰 | 掷出全部剩余骰；点数分布正确 |
| 选择 | 只能选掷出结果中存在的点数；不存在 → 拒绝 |
| 放置 | 必须放该点数的**全部**骰子；部分放置 → 拒绝 |
| 轮转 | 顺时针；骰子用尽自动 pass；轮末只剩一人可连动 |
| **平手作废** | 官方例 1（5/3/3/1 → 3 作废）；官方例 2（2/2/1/1 → 全灭） |
| 发钞 | 降序发钞；钞票不足时后排空手；每人每场最多 1 张 |
| 余钞 | 剩余钞票回到堆底且后续可再发出 |
| 轮次 | 恰好 4 轮后结束；起始玩家每轮左移 |
| 胜负 | 钱多者胜；平手比张数；再平手并列 |
| 变体 | 2 人各 +4 白骰；3/4 人各 +2；3 人局多余 2 白骰由起始玩家掷 |
| 中立骰 | 虚拟玩家参与比较；赢到的钞票回堆底 |
| 守恒 | 任意随机对局结束后钞票总数仍为 54 |
| 边界 | 空赌场；赌场只有 1 人；赌场钞票数 < 玩家数 |

**建议加一个随机对局压力测试**：随机模拟 1000 局完整游戏，每局断言 §12.2 的全部不变量，可捕获绝大多数规则实现错误。

### 16.2 集成测试

- WebSocket 消息往返：`roll` → `room_state.yourRoll`；`choose` → 广播。
- 非当前玩家操作被拒。
- 断线重连状态恢复。

实际落地在 `LasVegasRoomAiTest`（26 个用例，`@Nested` 分组）：
`Lobby` / `Playing` / `Restart` / `Spectating` 四组，其中 `Spectating` 覆盖
「所有人都能观战」「观战者不能操作」「迟到者自动转观战」「满员回落观战」
「观战者可在大厅入座」「当前点数公开」「离开立即刷新自己的视图」
「观战者可聊天」「颜色互不相同且已下发」。
另有一个用真实 WebSocket 连服务端的端到端脚本（44 项断言），
覆盖颜色、观战、公开点数、离开刷新、托管接手五条链路。

## 17. 实现顺序建议

| 阶段 | 内容 | 产出 |
|---|---|---|
| **1** | `LasVegasGame` 纯引擎 + 完整单测（含随机压力测试） | 规则 100% 正确，无 UI |
| **2** | `LasVegasRoom` + `/ws/lasvegas` + 协议 | 可用 `wscat`/脚本对局 |
| **3** | `LasVegas.vue` + 路由 + 两处导航按钮 | 可玩 |
| **4** | 动画/结算可视化/断线重连 | 体验完善 |
| **5** | 可选：扩展模块（老虎机、Biggies、Kickers） | 重开性 |

> 阶段 1 是关键：**规则引擎先于网络层完成并被测试覆盖**，能避免把规则 bug 和并发 bug 混在一起排查。

## 18. 已确认决策（原「待拍板」，现已全部落定）

1. **人数**：**支持 2–5 全人数**。✅ 已实现。
2. **AI 对手**：**先不做 → 后续已实现**。第一版确认「先不实现 AI 对手」，
   之后用户追加需求「房主可以添加 AI，投资逻辑自定，目标是赚更多钱，可尝试高风险操作」，
   现已按**蒙特卡洛推演 + 风险偏好**实现（比原计划的「简单启发式」强得多）。
   详见 [`las-vegas-ai.md`](./las-vegas-ai.md)。✅ 已实现。
3. **房间模型**：**先做单房间，但按可复用方式组织**。已通过 `LasVegasRoomRegistry`
   （`ConcurrentMap` + `computeIfAbsent`）落地，后续加房间号即可多桌。✅ 已实现。
4. **中立骰变体**：**不实现**（2 人局体验偏空，已在规则页 §9 明确披露）。✅ 已披露。
5. **存档/战绩**：**写库**。新增 `lasvegas_match` + `lasvegas_player` 两张表，
   登录玩家可在「战绩表」标签查看自己参与过的对局。✅ 已实现。
6. **是否重构 `GameRoom`**：**不重构**。拉斯维加斯是**完全独立**的架构，
   五子棋的 `GameRoom` 一行未动。✅ 已遵守。
7. **观战**：**所有人都能观战，但只有参赛者能操作**。第一版只做了「观战者列表」占位，
   后按用户需求落地为真正的只读观战（见 §14.3）。权限边界靠数据结构保证
   （观战者不进 `members`/`sessionToKey`），不靠逐处 if。✅ 已实现。
8. **颜色可见性**：**每人固定一个颜色，自己和他人都能看到**。颜色在加入时确定，
   不随座位变动，与前端 `DICE_COLORS` 保持同序（红蓝绿橙紫）。✅ 已实现。
9. **中途退出**：**座位交给 AI 托管**。若只是保留座位，剩下的玩家会被永久卡住；
   托管后本人重连即收回控制权。✅ 已实现。

