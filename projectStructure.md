# 项目结构总览 (projectStructure.md)

> 基于 **Spring Boot 2.7 + Vue 3** 的全栈个人主页项目，集成技能展示、项目卡片，以及多人实时对战五子棋。

---

## 一、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端框架 | Vue 3 (Options API) | ^3.3.4 |
| 前端路由 | Vue Router | ^4.2.4 |
| 前端构建 | Vite | ^4.4.9 |
| 后端框架 | Spring Boot | 2.7.18 |
| 后端安全 | Spring Security + JWT (jjwt) | 0.11.5 |
| 实时通信 | Spring WebSocket | — |
| 数据库 | MariaDB (JDBC, mariadb-java-client) | 2.7.9 驱动 |
| 构建工具 | Maven / npm | — |
| JDK | 11+ | — |

---

## 二、目录结构

```
./
├── projectStructure.md             # 本文档
├── README.md                       # 项目说明
├── LICENSE                         # MIT 许可证
├── .gitignore                      # Git 忽略规则
├── .github/workflows/release.yml   # 打 v* tag 自动构建并发 Release
├── .github/workflows/ci.yml        # push/PR 自动跑后端测试 + 前端构建检查
├── deploy/                         # Release 部署包内容
│   ├── start.sh                    # Linux/macOS 一键启动脚本
│   ├── start.bat                   # Windows 一键启动脚本
│   └── DEPLOY.md                   # 部署指南
├── frontend/                       # Vue 3 前端
│   ├── package.json                # 前端依赖
│   ├── vite.config.js              # Vite 配置 (dev:5173, 代理 /api → :8080)
│   └── src/
│       ├── main.js                 # 入口文件 (createApp)
│       ├── App.vue                 # 根组件 (导航栏+路由出口+页脚)
│       ├── router/
│       │   └── index.js            # 路由配置 (含守卫: 已登录访问登录页重定向)
│       ├── utils/
│       │   └── auth.js             # 认证工具 (Token管理/authFetch/logout)
│       ├── assets/
│       │   └── styles/
│       │       └── main.css        # 全局样式
│       └── views/                  # 页面组件
│           ├── Home.vue            # 首页
│           ├── Auth.vue            # 登录/注册页
│           └── Gomoku.vue          # 五子棋游戏
│
└── backend/                        # Spring Boot 后端
    ├── pom.xml                     # Maven 依赖
    ├── SECURITY.md                 # 账号系统安全架构说明
    ├── application-local.example.yml  # 私密配置模板（复制为 application-local.yml，勿提交）
    ├── src/main/
        ├── resources/
        │   ├── application.yml     # 应用配置 (端口/数据库/日志)
        │   └── static/             # 前端构建产物 (部署时自动生成)
        └── java/com/homepage/
            ├── HomepageApplication.java    # Spring Boot 入口
            ├── config/                     # 配置层
            │   ├── SecurityConfig.java     # Spring Security 配置 (放行/认证规则)
            │   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
            │   ├── JwtUtil.java            # JWT 生成/解析工具 (双 Token)
            │   ├── WebMvcConfig.java       # CORS + 静态资源配置
            │   ├── WebSocketConfig.java    # WebSocket 端点注册 (/ws/game)
            │   ├── DataInitializer.java    # 启动初始化 (全部建表 + 管理员初始化，凭据来自配置)
            │   └── GlobalExceptionHandler.java  # 全局异常处理
            ├── controller/                 # 控制器层 (REST API)
            │   ├── ApiController.java      # 通用 API (/api/health|profile|skills|projects)
            │   ├── AuthController.java     # 认证 API (/api/auth/*)
            │   └── VisitorController.java  # 访问统计 API (/api/visitor/*)
            ├── service/                    # 服务层
            │   └── UserService.java        # 用户服务 (注册/登录/刷新Token)
            ├── repository/                 # 数据访问层 (JdbcTemplate)
            │   ├── UserRepository.java     # 用户 CRUD (含角色管理)
            │   └── VisitorRepository.java  # 访问统计 CRUD (表 visitor_log)
            ├── model/                      # 数据模型
            │   ├── User.java               # 用户实体
            │   └── VisitorRecord.java      # 访问记录实体 (对应表 visitor_log)
            └── game/                       # 游戏引擎 (WebSocket)
                ├── GameRoom.java           # 五子棋房间 / WebSocket 消息处理
                ├── GomokuGame.java         # 五子棋引擎 (棋盘/落子/胜负)
                ├── GomokuAI.java           # 五子棋 AI (权重评估算法)
                └── GameWebSocketHandler.java # WebSocket 握手处理器

    backend/src/test/java/com/homepage/   # 单元测试 (JUnit 5 + AssertJ)
        ├── game/GomokuGameTest.java      # 五子棋引擎: 胜负判定/非法落子/悔棋/重开
        ├── game/GomokuAITest.java        # 五子棋 AI: 开局/成五/封堵/攻防优先级
        └── config/JwtUtilTest.java       # JWT: 签发解析往返/双密钥隔离/防篡改
```

---

## 三、数据库表

全部 2 张表由 `DataInitializer` 在应用启动时自动创建（`CREATE TABLE IF NOT EXISTS`，全新数据库可直接启动）：

| 表名 | 用途 | 访问层 |
|------|------|--------|
| `users` | 用户表（用户名/密码/角色/启用状态/登录失败次数） | `UserRepository` |
| `visitor_log` | 访问记录 (id, ip, page, created_at) | `VisitorRepository` |

**建表与初始化**（`DataInitializer`，应用启动时先建表再初始化管理员）：
- `CREATE TABLE IF NOT EXISTS` 创建全部 2 张表，统一 utf8mb4_general_ci
- 若配置了 `app.admin.username` / `app.admin.password`（环境变量 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 或 gitignore 掉的 `application-local.yml`）且该用户不存在，自动创建 ADMIN 角色管理员；未配置则跳过并告警

**其他要点**：
- 数据库：MariaDB `homepage`，字符集 utf8mb4
- Refresh Token 为自包含 JWT（签名校验即信任），**不落库**，无 refresh_tokens 表
- 五子棋对战状态**不入库**：保存在服务端内存中

### 用户角色
- `USER` — 普通用户
- `ADMIN` — 管理员

---

## 四、API 接口一览

### 通用接口（无需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/profile` | 个人信息 |
| GET | `/api/skills` | 技能列表 |
| GET | `/api/projects` | 项目列表 |

### 认证接口 (`AuthController`)

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 公开 |
| POST | `/api/auth/login` | 用户登录 (返回 accessToken + refreshToken) | 公开 |
| POST | `/api/auth/refresh` | 刷新 Token | 公开 |
| GET | `/api/auth/me` | 获取当前登录用户信息 | 登录 |

### 访问统计接口 (`VisitorController`，公开)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/visitor/ping` | 记录访问 (去重) |
| GET | `/api/visitor/count` | 获取访问统计 (总访问/独立访客/今日) |

### WebSocket

| 路径 | 说明 |
|------|------|
| `/ws/game` | 五子棋实时通信（房间制） |

---

## 五、前端路由表

| 路径 | 组件 | 名称 | 说明 | meta |
|------|------|------|------|------|
| `/` | `Home.vue` | Home | 首页 (个人介绍/技能/项目/功能入口) | `{ title: 'chycal 的个人首页' }` |
| `/login` | `Auth.vue` (懒加载) | Auth | 登录/注册 | `{ title: '登录 - chycal', guest: true }` |
| `/gomoku` | `Gomoku.vue` (懒加载) | Gomoku | 五子棋对战 | `{ title: '五子棋 - chycal' }` |

路由守卫行为：
- `guest: true` → 已登录用户访问时重定向到 `/`
- 每次跳转根据 `meta.title` 设置 `document.title`

---

## 六、核心功能模块说明

### 1. 首页 (`Home.vue`)
- 个人信息展示 (头像、姓名、简介)
- 技能标签云
- 项目卡片列表
- 功能快速入口 (五子棋)

### 2. 五子棋 (`Gomoku.vue`)
- WebSocket (`/ws/game`) 实时通信，房间制多人对战
- 支持观战 (无限观战者)
- 按阵营自主落座，赛前可自由离座
- AI 对手 (基于局面评估的权重算法)
- 悔棋 (撤回双方各一步)、求和、重开、认输
- 评论区 (最近 10 条消息，观战者与玩家均可发言)

### 3. 认证系统
- JWT 双 Token 机制: Access Token (短期) + Refresh Token (长期，自包含 JWT，不落库)
- `authFetch` 自动处理 Token 过期刷新
- 登录状态通过 `localStorage` + `CustomEvent('auth-change')` 跨组件同步
- `App.vue` 导航栏根据登录状态动态显示 (登录按钮 → 用户名/退出)

---

## 七、关键配置文件

| 文件 | 用途 |
|------|------|
| `frontend/vite.config.js` | dev 端口 5173，`/api` 代理到 `:8080`，构建输出到 `backend/.../static` |
| `frontend/package.json` | 依赖: vue ^3.3.4, vue-router ^4.2.4, vite ^4.4.9 |
| `backend/pom.xml` | Spring Boot 2.7.18, Spring Security, WebSocket, jjwt 0.11.5, mariadb-java-client 2.7.9 |
| `backend/src/main/resources/application.yml` | 端口 8080, 数据源（凭据走环境变量占位符）, `spring.config.import` 加载本地私密配置 |
| `backend/application-local.example.yml` | 私密配置模板：复制为 `application-local.yml` 填入真实值（已 gitignore） |
| `.github/workflows/release.yml` | 打 `v*` tag 触发：npm 构建前端 → mvn 打包 → jar + 启动脚本打成 bundle 发 GitHub Release |
| `.github/workflows/ci.yml` | push(main)/PR 触发：`mvn test` 后端测试 + 前端构建检查 |

---

## 八、开发注意事项

1. **修改新增功能时**: 必须在 `App.vue` 顶部导航栏添加跳转链接，并在 `Home.vue` 功能入口区添加按钮。
2. **编译前**: 先停止正在运行的 Java 后端进程，防止内存不足 (JVM 编译 + 运行双进程)。
3. **构建部署流程**:
   ```bash
   # 前端构建
   cd frontend && npm run build   # 输出到 backend/.../static
   # 后端构建
   cd backend && mvn package -DskipTests -q
   # 启动
   java -jar target/homepage-backend-1.1.0.jar
   ```
4. **前端懒加载**: 除 Home 外所有页面组件均使用动态 `import()`，减小首屏体积。
5. **认证流程**: 需要认证的 API 由 Spring Security + `JwtAuthenticationFilter` 保护（默认规则 `/api/**` 需认证），前端使用 `authFetch` 自动附加/刷新 Bearer Token。
6. **访客统计去重**: 前端用 `sessionStorage` 标记 + 后端 IP 去重，同会话不重复计数。
7. **WebSocket 端点**: 注册在 `/ws/game`（五子棋），`SecurityConfig` 放行 `/ws/**`。
8. **私密配置**: 数据库凭据与管理员账号只放 `application-local.yml`（已 gitignore）或环境变量，不写入代码库。
9. **发版流程**: 更新版本后打 tag（`v1.0.x`）推送，GitHub Actions 自动构建并发布 Release；`deploy/` 目录内容会打进发布包。
10. **测试**: 核心纯逻辑有 JUnit 5 单元测试（五子棋引擎与 AI、JWT 工具，位于 `backend/src/test`），push/PR 时 CI 自动运行；改动对应逻辑请同步维护测试。
