# 拉斯维加斯 AI 对手：投资逻辑设计

> 面向维护者。回答两个问题：**AI 怎么决定押哪个点数**，以及**它的「高风险操作」是怎么来的**。
>
> 对应代码：
> - 决策逻辑 `backend/src/main/java/com/homepage/lasvegas/LasVegasAi.java`
> - 房间集成 `backend/src/main/java/com/homepage/lasvegas/LasVegasRoom.java`
> - 单元测试 `backend/src/test/java/com/homepage/lasvegas/LasVegasAiTest.java`（19 个）
> - 房间测试 `backend/src/test/java/com/homepage/lasvegas/LasVegasRoomAiTest.java`（14 个）

---

## 1. 需求

来自用户的原话：

> 现在可以开始写 AI 功能了，要求是房主可以添加 AI，AI 的投资逻辑你来解决，
> 能赚更多的钱就可以，也可以判断尝试一些高风险操作（指 AI 的运行逻辑）。

拆成四条硬性要求：

| # | 要求 | 落点 |
|---|---|---|
| 1 | **房主**可以添加 AI | `LasVegasRoom.onAddAi`，仅 `hostKey()` 且 `LOBBY` 阶段 |
| 2 | 投资逻辑由实现方决定 | 本文档 |
| 3 | 目标是**赚更多的钱** | 期望收益（EV）最大化，见 §3 |
| 4 | 可以**判断后尝试高风险操作** | `aggression` 风险偏好，见 §4 |

要求 4 说的是「AI 的运行逻辑可以冒险」，**不是**「系统允许高风险运维」。
AI 的冒险完全在游戏规则内：赌一把高价值赌场，代价是可能颗粒无收。

---

## 2. 为什么「骰子多」不等于「能赚钱」

这是设计整个 AI 的核心约束，必须先说清楚。

官方结算规则（`LasVegasPayout.settle`）：某个赌场上**骰子数相同的玩家全部作废**，
剩下的人按骰子数降序，依次领走面额从高到低的钞票。

于是：

```
赌场 3（The Mirage）：
  我 4 颗  ─┐
  对手 4 颗 ─┴─ 两人骰子数相同 → 全部作废，谁都拿不到钱
  对手 2 颗 → 反而拿走了最高的那张钞票
```

所以「我放了 4 颗」这个局部信息**完全没有意义**。真实收益取决于**最终**的相对排名，
而对手在我之后还会继续放骰子。AI 必须估计「对手接下来会怎么放」——
这就排除了「数一数自己骰子最多就选它」这种朴素做法（下文的 `骰子最多` 基线，实测被 AI 大幅碾压）。

---

## 3. 决策模型：蒙特卡洛推演

### 3.1 候选集合

一次行动中，玩家掷出全部剩余骰子，然后**必须**选一个点数，
把该点数的**全部**骰子放进对应赌场（不能拆开）。

所以候选就是「本次掷出的结果里出现过哪些点数」。例如掷出 `[3,3,5,5,5]`，
候选是 `{3 → 2 颗, 5 → 3 颗}`，不能选 1。

`LasVegasAi.optionsOf(List<Integer>)` 负责去重计数。

### 3.2 每个候选做 100 次推演

对每个候选：

1. 复制当前局面（`View`），把该点数的全部骰子放到对应赌场；
2. 按**对手模型**（§5）把本轮剩余的所有行动模拟完；
3. 用官方结算规则算出**这一轮** AI 拿到多少钱。

关键实现细节：结算直接复用 `LasVegasPayout.settle`，
而不是在 AI 里重写一份。这样「AI 的预期」与「真实对局」永远一致，
不会出现 AI 算错规则的隐蔽 bug。

### 3.3 打分公式

推演 100 次后得到 100 个收益样本，取两个统计量：

- `EV`：平均收益（期望）
- `q`：90% 分位（`HIGH_QUANTILE = 0.90`），代表「运气好的时候能拿到多少」

最终打分：

```
score = EV + aggression × (q − EV)
```

含义很直白：

- `aggression = 0` → 只看平均值，**选稳的**；
- `aggression = 1` → 完全按上限打分，**选上限最高的**，哪怕经常空手；
- 中间值 → 在「稳」和「搏」之间按比例折中。

这就是「投资逻辑」的全部：**不是拍脑袋的启发式，而是把每个选项的真实期望和上限都算出来，再按风险偏好加权。**

### 3.4 共同随机数（降低噪声）

推演是随机的，如果每个候选都用不同的随机序列，
「A 比 B 好」可能只是 A 抽到了好运。因此用**共同随机数**技巧：
第 `k` 次推演在所有候选中共用同一个种子

```
baseSeed + k × 0x9E3779B97F4A7C15L
```

（黄金比例常数，用于打散相邻种子）。
这样候选之间的差异来自**决策本身**，而不是随机噪声，比较才公平。

### 3.5 单候选短路

如果本次掷出的所有骰子都是同一个点数（只有 1 个候选），
没有可选项，直接返回，跳过 100 次推演。省掉一整轮无意义的计算。

---

## 4. 高风险操作是怎么「判断」出来的

`aggression`（0–1）是唯一的风险旋钮，它由**两个因素**相乘/相加决定：

```java
aggression = styleBase + 0.65 × min(1, deficit / threshold)
```

### 4.1 因素一：固有性格（`Style`）

添加 AI 时由房主选择，名字会显示在大厅里：

| Style | 中文 | `styleBase` | 行为 |
|---|---|---|---|
| `SOLID` | 稳健 | 0.05 | 基本只看期望，优先拿稳的钱 |
| `BOLD` | 激进 | 0.30 | 期望相近时偏向高上限的打法 |
| `WILD` | 赌徒 | 0.55 | 明显落后时敢赌翻盘 |

### 4.2 因素二：当前落后程度

```
threshold = TOTAL_VALUE / TOTAL_ROUNDS / players × roundsLeft
```

即「按人头、按剩余轮次平摊，我大概还该拿多少」。

- `deficit = 领先者金额 − 我的金额`（不落后则为 0）
- 落后越多 → `deficit / threshold` 越大 → `aggression` 越高
- 上限 `+0.65`，最终 `clamp` 到 `[0, 1]`

于是同一只「稳健」AI 在领先时安分守己，在快输的时候也会去搏一把；
「赌徒」则从一开始就更愿意冒险。**这才是「判断之后再去赌」，而不是无脑乱赌。**

### 4.3 对照基线（实测）

200 局 / 配置，AI 固定 0 号座（`/tmp/aibench/out.txt`）：

| 配置 | AI 均额 | AI 胜率 | 对手均额 |
|---|---|---|---|
| 稳健 vs 3×「骰子最多」 | 551,500 | 87.0% | 309k–322k |
| 激进 vs 3×「骰子最多」 | 545,750 | 86.0% | — |
| 赌徒 vs 3×「骰子最多」 | 540,450 | 84.5% | — |
| 激进 vs 3×「随机乱放」 | 485,200 | 71.5% | 304k–319k |
| 激进 vs 3×「单步评估」 | 445,200 | 60.5% | 362k–367k |

结论：**三种性格都碾压所有基线**，包括最强的「单步评估」。
稳健对弱对手最强；对手越强（单步评估），激进/赌徒的相对优势越明显——
这与「落后时才该冒险」的设计意图一致。

---

## 5. 对手模型

真实对手是人，行为不可知。与其假设「大家都很理性」，不如**每次推演随机抽一种模型**，
让 AI 不依赖任何单一假设（也是为了防止 AI 被某一种打法针对性克制）：

| 模型 | 概率 | 行为 |
|---|---|---|
| 理性型 | 80% | 按一手评估选点：`收益 × 被追平风险折扣` |
| 莽夫型 | 10% | 只看骰子数，喜欢往人多的地方堆 |
| 新手型 | 10% | 在可选点数里随机乱放 |

一手评估（`onePlyScore`）的细节：

```java
// 领先几个人就能拿钱：只有「我前面没人并列」时才有收益
gain = notesDesc[value][ahead]        // 当 tied == 0 && ahead < notes

// 被追平的风险折扣
risk = max(0.05, 1.0 − 0.45 × tied − 0.18 × vulnerable)
```

- `tied`：当前已有几人跟我骰子数相同（相同即作废，越多越危险）
- `vulnerable`：还能超过我的人数（别人手里还有骰子）
- 下限 `0.05`，避免除零 / 全零

**AI 自己后续的回合永远按理性型模拟**——即「我以后会好好下」，
而对手则会犯错。这让 AI 在评估时既不低估自己，也不高估对手。

---

## 6. 房间集成

`LasVegasAi` 是纯逻辑（无 Spring / WebSocket / 数据库），
自动行动由 `LasVegasRoom` 驱动。

### 6.1 AI 是「特殊成员」

`Member` 多了两个字段：`boolean ai` 和 `LasVegasAi.Style aiStyle`。

AI 成员的约定：

- `session == null`、`userId == null`、`connected == true`
- key 形如 `ai:1`、`ai:2`（`aiSeq` 自增），与真人的 `u:<id>` / `g:<guestId>` 并列
- 名字 `电脑<N>（<性格>）`

### 6.2 谁能添加

`onAddAi` 的校验顺序：

1. 必须已加入房间 → `请先加入房间`
2. 必须是房主 → `只有房主可以添加电脑对手`
3. 必须在大厅阶段 → `对局已开始，不能再添加电脑对手`
4. 未满 5 人 → `房间已满（最多 5 人）`

`hostKey()` 会**跳过 AI**，所以 AI 永远不会成为房主——
否则「房主添加 AI」会变成「AI 给自己添加 AI」。

### 6.3 AI 怎么自动走

不在 `LasVegasGame` 里塞任何 AI 逻辑（保持引擎纯净），
而是由房间在**每次广播状态之后**调用 `scheduleAiTurn()`：

```
scheduleAiTurn()
  ├─ 仅在 PLAYING / game != null / !game.isOver() 时才继续
  ├─ 当前玩家必须是 AI 成员
  └─ 延迟 aiStepDelayMs 后执行 runAiTurn()
        ├─ 还没掷骰 → game.roll()
        └─ 已掷骰   → game.choose(LasVegasAi.chooseValue(...))
        └─ 结束后 persistResult() + broadcastState() + scheduleAiTurn()
```

调用点：`onStart`（开局后）和 `onChoose`（每次有人选完点数后）。
AI 走完后会再次 `scheduleAiTurn()`，形成链式驱动——
如果下一个还是 AI，它会继续走，直到轮到真人为止。

`aiStepDelayMs` 默认 700ms，让 AI 有「思考感」，玩家能看清每一步。
测试里用 `setAiStepDelayMs(0)` 让它瞬间走完。

### 6.4 为什么用单线程调度器

`aiExecutor()` 懒创建一个名为 `lasvegas-ai-<roomCode>` 的**守护**单线程调度器。

- 单线程 → 同一个房间的 AI 行动天然串行，不会并发改同一局游戏；
- 守护线程 → JVM 退出时不会挂住；
- 按房间命名 → 多房间时日志可区分（为后续多房间预留）。

`aiTurnScheduled` 标志位防止同一步被重复调度（例如两个真人的消息几乎同时到达）。

### 6.5 生命周期

| 场景 | 行为 |
|---|---|
| 重开一局（`restart`） | **保留** AI，回到大厅后仍可移除 |
| 真人全部掉线 | 清空 AI（`pruneAiIfNoHuman`），避免留下无人可玩的空房间 |
| 移除某个 AI | `renumberAi()` 重命名其余 AI（`电脑2` → `电脑1`），保持编号连续 |
| 移除真人 | 不允许；`onRemoveAi` 对非 AI 目标报 `找不到该电脑对手` |

### 6.6 战绩表

AI 会写 `lasvegas_player` 行，但 `user_id = NULL`。

这是**刻意**的：`LasVegasRepository.findMatchesByUser` 用 `p.user_id = ?` 关联查询，
`NULL` 永远不匹配，所以 AI 的成绩不会出现在任何人的战绩表里。
测试 `aiResultsArePersisted` 明确断言「真人 `user_id = 42`，AI `user_id = null`」。

---

## 7. 前端

`frontend/src/views/LasVegas.vue` 大厅里：

- **添加**：房主可见 `.lv-add-ai` 区块——性格下拉框（`state.aiStyles`）+「＋ 添加」按钮
- **移除**：AI 那一行显示 `.lv-remove-ai` 的 ✕ 按钮（仅 `state.canRemoveAi`）
- **标识**：AI 玩家带 `🤖 电脑` 徽章（`.lv-badge.ai-badge`），与真人视觉区分

服务端下发的字段：`canAddAi`、`canRemoveAi`、`aiStyles`（`{value, label}` 列表），
玩家条目多了 `ai`（AI 还有 `aiStyle` / `aiStyleLabel`）。

---

## 8. 测试

```bash
cd backend && mvn -o test
```

全量 **111 个测试**，其中 AI 相关 33 个：

- `LasVegasAiTest`（19 个）
  - `Basics`（5）：候选去重、View 快照、非法入参
  - `Choosing`（7）：偏好高价值赌场、不送人头、平手加注、避开必被作废的点
  - `Aggression`（3）：落后时 aggression 上升、性格基线、范围钳制
  - `Gameplay`（2）：随机对局不崩、结算守恒
  - `Strength`（2）：对「骰子最多」和「随机乱放」基线各 40 局，AI 必须赢
- `LasVegasRoomAiTest`（14 个）
  - `Lobby`（8）：添加、选性格、仅房主、满员拒绝、移除后重编号、拒绝移除真人、AI 不当房主、无真人时清理
  - `Playing`（4）：AI 自己走完、对局中禁止增删、AI 不阻塞真人、废弃对局不卡死房间
  - `Restart`（2）：重开保留 AI、AI 战绩以 `user_id = NULL` 落库

> Java 11 限制：`@Nested` 内部类**不能**声明 `static` 方法，
> 所以测试辅助方法都放在外层类上。另外 Surefire 2.22.2 的 `-Dtest=` 匹配不到 `@Nested` 类，
> 必须跑全量 `mvn -o test`。

---

## 9. 调参速查

| 常量 / 参数 | 位置 | 默认 | 作用 |
|---|---|---|---|
| `DEFAULT_ROLLOUTS` | `LasVegasAi` | 100 | 每个候选的推演次数；越大越准越慢 |
| `HIGH_QUANTILE` | `LasVegasAi` | 0.90 | 「高上限」的分位；越小越保守 |
| `styleBase` | `Style` | 0.05 / 0.30 / 0.55 | 性格的冒险基线 |
| `0.65` | `aggressionOf` | 0.65 | 落后程度的放大系数 |
| `aiStepDelayMs` | `LasVegasRoom` | 700 | AI 行动间隔（ms） |

想调「AI 多聪明」→ 动 `DEFAULT_ROLLOUTS`（注意 CPU：100 次 × 候选数 × 玩家数）；
想调「AI 多敢赌」→ 动 `styleBase` 或那个 `0.65`。
