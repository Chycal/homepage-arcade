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

沿用现有 `{"type": "...", ...}` 风格（见 `GameRoom.handleMessage`）。

### 14.1 客户端 → 服务端

```json
{ "type": "join",   "userId": "chycal" }
{ "type": "sit",    "seat": 0 }
{ "type": "ready" }
{ "type": "start" }
{ "type": "roll" }
{ "type": "choose", "value": 3 }
{ "type": "leave" }
{ "type": "chat",   "text": "..." }
```

### 14.2 服务端 → 客户端

```json
{ "type": "room_state", "status": "waiting|playing|over",
  "seats": [ {"seat":0,"user":"chycal","color":0,"money":120000,"banknotes":3,"diceLeft":8}, ... ],
  "spectators": ["..."], "spectatorCount": 0, "yourSeat": 0 }

{ "type": "round_start", "round": 2, "startPlayer": 1,
  "casinos": [ {"dieValue":1,"banknotes":[30000,20000],"total":50000}, ... ] }

{ "type": "your_roll", "dice": [3,3,3,5,1,2,6,3],
  "options": [ {"value":3,"count":4}, {"value":5,"count":1}, ... ] }

{ "type": "placed", "seat": 0, "value": 3, "count": 4, "casino": 3 }

{ "type": "payout", "round": 1,
  "casinos": [ {"dieValue":1, "voided":[1,2], "awards":[{"seat":0,"note":40000}], "leftover":[10000]} ],
  "totals": [ {"seat":0,"money":120000,"banknotes":3} ] }

{ "type": "game_over", "ranking": [ {"seat":0,"money":310000,"banknotes":9}, ... ],
  "winners": [0] }

{ "type": "error", "message": "..." }
```

> 设计原则：**每个客户端只应看到自己该看的**。`your_roll` 只发给投骰者；其他人只收到 `placed`（知道放了什么，但看不到他手牌）。

### 14.3 重连处理

现有 `GameRoom.handleDisconnect` 在玩家断线时**直接重开**。拉斯维加斯是 4 轮长局，断线直接重开体验很差。建议：
- 断线 → 标记 `offline`，**保留座位和状态**，给 30–60 秒重连窗口。
- 超时未回 → 自动 pass（跳过其回合），游戏继续。
- 观战者断线 → 直接移除，不影响对局。

## 15. 前端设计

### 15.1 新增文件

```
frontend/src/views/LasVegas.vue     ← 主视图
frontend/src/utils/lasvegas.js      ← 可选：状态/颜色/格式化辅助
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

### 15.4 界面区块

| 区块 | 内容 |
|---|---|
| **赌场区** | 6 个赌场卡（点数 + 当前钞票面额列表 + 已放骰子按玩家颜色分组显示） |
| **我的骰子区** | 剩余骰子；投骰后高亮可选点数，显示「放 N 个 3」按钮 |
| **玩家面板** | 每人的颜色、剩余骰数、已赢金额、钞票张数 |
| **轮次/回合提示** | 「第 2 / 4 轮」「轮到 chycal」 |
| **观战区** | 观战者列表（复用五子棋样式） |
| **评论/聊天** | 复用五子棋的 `chat` + `comments` 机制 |

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

- WebSocket 消息往返：`roll` → `your_roll`；`choose` → `placed` → 广播。
- 非当前玩家操作被拒。
- 断线重连状态恢复。

## 17. 实现顺序建议

| 阶段 | 内容 | 产出 |
|---|---|---|
| **1** | `LasVegasGame` 纯引擎 + 完整单测（含随机压力测试） | 规则 100% 正确，无 UI |
| **2** | `LasVegasRoom` + `/ws/lasvegas` + 协议 | 可用 `wscat`/脚本对局 |
| **3** | `LasVegas.vue` + 路由 + 两处导航按钮 | 可玩 |
| **4** | 动画/结算可视化/断线重连 | 体验完善 |
| **5** | 可选：扩展模块（老虎机、Biggies、Kickers） | 重开性 |

> 阶段 1 是关键：**规则引擎先于网络层完成并被测试覆盖**，能避免把规则 bug 和并发 bug 混在一起排查。

## 18. 待确认决策（开工前需拍板）

1. **人数**：支持 2–5 全人数，还是先只做 3–4 人？
2. **AI 对手**：五子棋有 AI，拉斯维加斯是否也要？若要，做简单启发式（优先争夺当前面额最高的赌场）即可。
3. **房间模型**：单房间（像五子棋，全站共用一个）还是多房间（可开多桌）？多房间需要房间列表 + 房间号。
4. **中立骰变体**：是否实现？不实现则 2 人局体验会很空。
5. **存档/战绩**：是否把对局结果写库（新增表）？现有 `visitor_log` 模式可参考。
6. **是否重构 `GameRoom`**：走 §11.2 的折中方案（新增并行房间）还是做通用 `GameEngine` 重构？
