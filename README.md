# Personal Homepage & Games

[![CI](https://github.com/Chycal/homepage-arcade/actions/workflows/ci.yml/badge.svg)](https://github.com/Chycal/homepage-arcade/actions/workflows/ci.yml)

基于 **Spring Boot 2.7 + Vue 3** 的全栈个人主页项目，集成技能展示、项目卡片，以及多人实时对战五子棋，并带用户认证与访问统计。

## 功能特性

- **个人主页** — 个人信息、技术栈、项目展示、服务健康监控
- **五子棋对战** — 多人实时对战，支持观战、AI 对手、悔棋、求和、重开、评论区
- **用户系统** — JWT 双 Token 认证（Access + Refresh），注册 / 登录 / 自动续期
- **访问统计** — 记录访客页面访问，提供总访问量、独立访客数与今日访问数
- **实时通信** — WebSocket 驱动五子棋房间状态同步

## 快速部署（Release 包）

从 [Releases](https://github.com/Chycal/homepage-arcade/releases) 下载 `homepage-arcade-<版本>-bundle.zip`，解压后：

```bash
./start.sh        # Windows 双击 start.bat
# 首次运行会生成 application-local.yml，填入数据库与管理员配置后再运行一次
```

数据表全部自动创建，详见 [deploy/DEPLOY.md](deploy/DEPLOY.md)。

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
├── .github/workflows/release.yml # 打 v* tag 自动构建并发 Release
├── .github/workflows/ci.yml      # push/PR 自动跑测试与前端构建检查
├── deploy/                       # Release 部署包内容（启动脚本 + 部署指南）
│   ├── start.sh                  # Linux/macOS 一键启动
│   ├── start.bat                 # Windows 一键启动
│   └── DEPLOY.md                 # 部署指南
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
│           └── Gomoku.vue        # 五子棋对战
│
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   ├── SECURITY.md               # 账号系统安全架构说明
│   ├── application-local.example.yml  # 私密配置模板（复制为 application-local.yml）
│   └── src/
│       ├── main/
│       │   ├── resources/
│       │   │   ├── application.yml   # 端口 / 数据库 / 日志
│       │   │   └── static/           # 前端构建产物
│       │   └── java/com/homepage/
│       │       ├── HomepageApplication.java  # 应用入口
│       │       ├── config/       # Security / JWT 过滤器 / WebSocket(/ws/game) / 启动初始化 / 全局异常
│       │       ├── controller/   # REST API 控制器（见下文接口表）
│       │       ├── service/      # 用户服务
│       │       ├── repository/   # JdbcTemplate 数据访问层
│       │       ├── model/        # 实体模型
│       │       └── game/         # 五子棋引擎 / AI / WebSocket 房间
│       └── test/java/com/homepage/  # JUnit 5 单元测试
│
└── projectStructure.md           # 项目结构文档
```

## 从源码构建

### 环境要求

- **JDK 11+**
- **Maven 3.6+**
- **Node.js 16+**
- **MariaDB / MySQL**

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
java -jar target/homepage-backend-1.1.0.jar
```

启动后访问：**http://localhost:8080**

### 数据库初始化

全部 2 张表由 `DataInitializer` 在应用首次启动时自动创建（`CREATE TABLE IF NOT EXISTS`，全新数据库可直接启动），无需手动执行建表脚本：

| 表名 | 用途 |
|------|------|
| `users` | 用户表（用户名/密码/角色/启用状态） |
| `visitor_log` | 访问记录（ip, page） |

- 用户角色：`USER` 普通用户 / `ADMIN` 管理员；配置了 `app.admin.username` / `app.admin.password` 时，若该用户不存在会自动创建 ADMIN 账号
- Refresh Token 为自包含 JWT，不落库；五子棋对局状态保存在服务端内存中

## 前端页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | Home | 首页（个人信息/技能/项目/功能入口） |
| `/login` | Auth | 登录/注册（已登录自动跳回首页） |
| `/gomoku` | Gomoku | 五子棋对战 |

路由守卫：已登录用户访问 `/login` 自动跳回首页。除 Home 外所有页面均懒加载。

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

## 五子棋游戏功能

- 房间制多人对战，支持无限观战者
- 按阵营自主落座，比赛前可自由离座
- **AI 对手**：真实玩家可部署 AI 到空位，AI 自动行棋并同意请求
- 悔棋（撤回双方各一步）、求和、重开、认输
- 评论区保留最近 10 条消息，观战者与玩家均可发言

## 访问统计

- 前端 `sessionStorage` 标记 + 后端 IP 去重，同会话不重复计数
- 提供总访问量、独立访客数与今日访问数

## 开发注意事项

1. **新增功能页面**：需在 `App.vue` 顶部导航栏添加跳转链接，并在 `Home.vue` 功能入口区添加按钮
2. **编译前**：先停止正在运行的 Java 后端进程，防止内存不足（JVM 编译 + 运行双进程）
3. **前端懒加载**：除 Home 外所有页面组件均使用动态 `import()`，减小首屏体积
4. **WebSocket**：五子棋端点注册在 `/ws/game`
5. **私密配置**：真实凭据只放 `application-local.yml`（已 gitignore）或环境变量，勿提交到仓库
6. **发版**：打 tag（如 `v1.0.1`）并推送，GitHub Actions 自动构建前端+后端并发布 Release（jar + 启动脚本 bundle）

## 测试

核心纯逻辑配有 JUnit 5 单元测试（`backend/src/test`），覆盖五子棋胜负判定与 AI 攻防、JWT 签发/解析/防篡改。push 与 PR 时 CI 自动运行，也可手动执行：

```bash
cd backend
mvn test
```

## License

MIT
