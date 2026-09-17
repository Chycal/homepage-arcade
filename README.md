# Personal Homepage & Games

基于 **Spring Boot 2.7 + Vue 3** 的全栈个人主页项目，集成技能展示、项目卡片，以及贪吃蛇、五子棋、24点、自走棋、挂机生活五个小游戏，并带用户认证系统与管理面板。

## 功能特性

- **个人主页** — 个人信息、技术栈、项目展示、服务健康监控
- **五子棋对战** — 多人实时对战，支持观战、AI 对手、悔棋、求和、重开、评论区
- **贪吃蛇** — 经典贪吃蛇游戏，支持键盘和触屏操控，含积分排行榜
- **24点** — 数学益智游戏，随机四张扑克牌用加减乘除算出24，含计分和记录
- **自走棋** — 自动对战棋：商店刷新、买卖棋子、上阵/撤下、升级、装备穿戴、自动战斗，会话可恢复
- **挂机生活** — 挂机打怪升级：开始/停止挂机、副本战斗、装备掉落、背包穿戴、药水补给，支持游客一键开局，存档服务端持久化
- **用户系统** — JWT 双 Token 认证（Access + Refresh），注册 / 登录 / 自动续期
- **管理面板** — 管理员查看/删除成绩记录，封禁/解封玩家
- **访问统计** — 记录访客页面访问，提供总访问量、独立访客数与今日访问数
- **实时通信** — WebSocket 驱动五子棋房间状态同步

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vue Router + Vite |
| 后端 | Spring Boot 2.7 + Spring Security (JWT) + WebSocket |
| 数据库 | MariaDB |
| 构建 | Maven + npm |

## 项目结构

```
.
├── frontend/                     # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js            # dev:5173，代理 /api → :8080，构建输出到 backend static
│   └── src/
│       ├── main.js               # 应用入口
│       ├── App.vue               # 根组件（导航栏 + 路由出口 + 页脚）
│       ├── router/index.js       # 路由配置 + 认证守卫
│       ├── utils/auth.js         # Token 管理 / authFetch / 登出
│       ├── assets/styles/main.css
│       └── views/
│           ├── Home.vue          # 首页
│           ├── Auth.vue          # 登录/注册
│           ├── Snake.vue         # 贪吃蛇
│           ├── Gomoku.vue        # 五子棋
│           ├── TwentyFour.vue    # 24点
│           ├── AutoChess.vue     # 自走棋
│           ├── IdleLife.vue      # 挂机生活
│           └── Admin.vue         # 管理面板
│
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   ├── SECURITY.md               # 账号系统安全架构说明
│   ├── application-local.example.yml  # 私密配置模板（复制为 application-local.yml）
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml   # 端口 / 数据库 / 日志
│       │   └── static/           # 前端构建产物
│       └── java/com/homepage/
│           ├── HomepageApplication.java  # 应用入口
│           ├── config/           # Security / JWT 过滤器 / WebSocket(/ws/game) / 启动初始化 / 全局异常
│           ├── controller/       # REST API 控制器（见下文接口表）
│           ├── service/          # 用户服务 / 挂机生活（另有已废弃的贪吃蛇防作弊遗留类）
│           ├── repository/       # JdbcTemplate 数据访问层
│           ├── model/            # 实体与游戏状态模型
│           └── game/             # 五子棋引擎 / AI / WebSocket 房间
│
└── projectStructure.md           # 项目结构文档
```

## 快速开始

### 环境要求

- **JDK 11+**
- **Maven 3.6+**
- **Node.js 16+**
- **MariaDB / MySQL**（排行榜、账号、挂机生活、管理面板等功能依赖数据库）

### 1. 配置私密信息

真实凭据不写入代码库。复制模板并填入你的数据库账号与初始管理员账号：

```bash
cp backend/application-local.example.yml backend/application-local.yml
# 编辑 backend/application-local.yml
```

`application.yml` 会自动加载运行目录下的 `application-local.yml`（在项目根目录或 `backend/` 下运行均可，放对应目录即可），也支持直接用环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `ADMIN_USERNAME` / `ADMIN_PASSWORD` 提供相同配置。

### 2. 构建前端

```bash
cd frontend
npm install
npm run build
```

构建产物自动输出到 `backend/src/main/resources/static/`。

### 3. 构建并启动后端

```bash
cd backend
mvn package -DskipTests
java -jar target/homepage-backend-1.0.0.jar
```

启动后访问：**http://localhost:8080**

### 数据库初始化

`DataInitializer` 在应用启动时自动执行：

- `CREATE TABLE IF NOT EXISTS` 创建 `banned_players`、`idle_life_saves`、`idle_life_items` 三张表
- 为存量表 `snake_scores` 自动补齐 `start_time` / `end_time` / `duration_seconds` 三列
- 若配置了 `app.admin.username` / `app.admin.password` 且该用户不存在，自动创建 ADMIN 角色管理员

注意：`users`、`snake_scores`、`visitor_log` 为存量表，需数据库中预先存在，启动时不会自动创建。

### 数据库表

| 表名 | 用途 |
|------|------|
| `users` | 用户表（用户名/密码/角色/启用状态） |
| `snake_scores` | 贪吃蛇成绩（含 start_time / end_time / duration_seconds 游戏用时） |
| `banned_players` | 封禁玩家 |
| `visitor_log` | 访问记录（ip, page, session_id） |
| `idle_life_saves` | 挂机生活玩家存档 |
| `idle_life_items` | 挂机生活背包物品 |

- 用户角色：`USER` 普通用户 / `ADMIN` 管理员
- 注意：`snake_scores` 与 `banned_players` 可能存在排序规则不一致，JOIN 查询需显式指定 `COLLATE utf8mb4_general_ci`
- Refresh Token 为自包含 JWT，不落库；自走棋对战状态不入库（内存 + `sessions/` 目录文件持久化，24 小时过期）

## 前端页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | Home | 首页（个人信息/技能/项目/功能入口） |
| `/login` | Auth | 登录/注册（已登录自动跳回首页） |
| `/snake` | Snake | 贪吃蛇 |
| `/gomoku` | Gomoku | 五子棋对战 |
| `/game24` | TwentyFour | 24点 |
| `/autochess` | AutoChess | 自走棋 |
| `/idle-life` | IdleLife | 挂机生活 |
| `/admin` | Admin | 管理面板（需登录 + ADMIN 角色） |

路由守卫：`requiresAuth` 未登录跳转 `/login`；`requiresAdmin` 非管理员跳回首页。除 Home 外所有页面均懒加载。

## API 接口

### 通用接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/profile` | GET | 个人信息 |
| `/api/skills` | GET | 技能列表 |
| `/api/projects` | GET | 项目列表 |

### 认证接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录（返回 accessToken + refreshToken） |
| `/api/auth/refresh` | POST | 刷新 Token |
| `/api/auth/me` | GET | 获取当前登录用户信息（需登录） |

### 贪吃蛇成绩接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/scores/submit` | POST | 提交成绩（需登录；body: `score` + `startTime`/`endTime` 毫秒时间戳，服务端计算用时） |
| `/api/scores/top` | GET | 排行榜（`?limit=N`，默认 10；自动过滤封禁玩家） |

### 24点接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/game24/deal` | GET | 随机发4张牌 |
| `/api/game24/verify` | POST | 验证24点算式 |

### 自走棋接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/autochess/new` | GET | 创建新游戏 |
| `/api/autochess/recover` | GET | 恢复已有会话 |
| `/api/autochess/state` | GET | 获取游戏状态 |
| `/api/autochess/refresh` | POST | 刷新商店 |
| `/api/autochess/buy` / `sell` | POST | 购买 / 出售棋子 |
| `/api/autochess/place` / `tobench` | POST | 上阵 / 撤下 |
| `/api/autochess/levelup` | POST | 升级人口 |
| `/api/autochess/battle` / `next` | POST | 战斗 / 下一回合 |
| `/api/autochess/equip` / `unequip` | POST | 穿戴 / 脱下装备 |

### 挂机生活接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/idle-life/guest` | GET | 创建游客账号并自动登录（公开） |
| `/api/idle-life/config` | GET | 游戏配置（副本/药水等，公开） |
| `/api/idle-life/save` | GET | 获取/创建当前用户存档 |
| `/api/idle-life/inventory` | GET | 背包 |
| `/api/idle-life/equip` / `unequip` | POST | 穿戴 / 脱下装备 |
| `/api/idle-life/potion` | POST | 使用药水 |
| `/api/idle-life/start` / `stop` | POST | 开始 / 停止挂机 |
| `/api/idle-life/battle` | POST | 手动战斗 |

### 管理接口（需 ADMIN）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/scores` | GET | 获取所有成绩记录 |
| `/api/admin/ban` / `unban` | POST | 封禁 / 解封玩家 |
| `/api/admin/scores/{id}` | DELETE | 删除成绩记录 |
| `/api/admin/banned` | GET | 获取封禁列表 |

### 访问统计接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/visitor/ping` | POST | 记录访问（同会话去重） |
| `/api/visitor/count` | GET | 总访问 / 独立访客 / 今日访问 |

### WebSocket

| 路径 | 说明 |
|------|------|
| `/ws/game` | 五子棋房间实时通信 |

## 认证系统

- JWT 双 Token：短期 Access Token + 长期 Refresh Token，`/api/auth/refresh` 续期
- `JwtAuthenticationFilter` 保护需要认证的 API（默认 `/api/**` 需认证，白名单见 `SecurityConfig`）
- 前端 `authFetch` 自动附加 Bearer Token，Token 过期时自动刷新重试
- 登录状态存 `localStorage`，通过 `CustomEvent('auth-change')` 跨组件同步，导航栏动态显示
- 安全设计详见 [backend/SECURITY.md](backend/SECURITY.md)

## 贪吃蛇游戏功能

- **双操控方式**：键盘（方向键 / WASD）与触屏 D-Pad，界面按钮开始 / 暂停
- **成绩提交需登录**：携带起止毫秒时间戳，服务端据此计算游戏用时并入库
- **排行榜**：服务端 LEFT JOIN 自动过滤封禁玩家
- **动态难度**：基础 100ms/步，每吃一个食物 -2ms，下限 50ms/步
- **移动端适配**：响应式布局 + 触屏 D-Pad

## 24点游戏功能

- 随机发四张扑克牌（A=1 ~ K=13），用加减乘除算出 24
- 四张牌必须全部使用且每张恰好一次
- 服务端用递归下降解析器安全求值表达式，仅允许数字和 `+ - * / ( )`
- 快捷填入按钮方便触屏输入，错误显示实际计算结果
- 得分累加 +1，换牌按钮随时重发（不保证有解），保留最近 10 条记录

## 五子棋游戏功能

- 房间制多人对战，支持无限观战者
- 按阵营自主落座，比赛前可自由离座
- **AI 对手**：真实玩家可部署 AI 到空位，AI 自动行棋并同意请求
- 悔棋（撤回双方各一步）、求和、重开、认输
- 评论区保留最近 10 条消息，观战者与玩家均可发言

## 自走棋游戏功能

- 商店刷新、购买、出售棋子，上阵/撤下与人口升级
- 装备穿戴系统
- 自动战斗结算，逐回合推进
- 会话可恢复（`recover`），服务端内存 + `sessions/` 目录文件持久化，24 小时过期

## 挂机生活功能

- **游客开局**：一键创建游客账号并自动登录，无需注册
- 挂机打怪：开始/停止挂机自动战斗，也可手动战斗
- 副本与药水配置由服务端下发
- 装备掉落、背包管理、穿/脱装备、使用药水
- 存档服务端持久化，换设备不丢进度

## 访问统计

- 前端 `sessionStorage` 标记 + 后端 IP 去重，同会话不重复计数
- 提供总访问量、独立访客数与今日访问数

## 开发注意事项

1. **新增功能页面**：需在 `App.vue` 顶部导航栏添加跳转链接，并在 `Home.vue` 功能入口区添加按钮
2. **编译前**：先停止正在运行的 Java 后端进程，防止内存不足（JVM 编译 + 运行双进程）
3. **数据库排序规则**：`snake_scores` 与 `banned_players` JOIN 时需显式指定 `COLLATE utf8mb4_general_ci`
4. **前端懒加载**：除 Home 外所有页面组件均使用动态 `import()`，减小首屏体积
5. **WebSocket**：五子棋端点注册在 `/ws/game`
6. **遗留死代码**：`GameSessionService`、`GameVerifier`、`GameRng`、`IpRateLimiter` 未被任何控制器引用（贪吃蛇服务器反作弊已移除的产物）
7. **私密配置**：真实凭据只放 `application-local.yml`（已 gitignore）或环境变量，勿提交到仓库

## License

MIT
