# 部署指南（Release jar 包）

从 [GitHub Releases](https://github.com/Chycal/homepage-arcade/releases) 下载 `homepage-arcade-<版本>-bundle.zip`，解压后 3 步完成部署。

## 前置要求

- **JDK 11+**（[Adoptium 下载](https://adoptium.net/)）
- **MariaDB / MySQL**（本机或远程均可）

## 部署步骤

### 1. 准备数据库

在 MariaDB/MySQL 中创建数据库（只需建库，表会自动创建）：

```sql
CREATE DATABASE IF NOT EXISTS homepage
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- 如使用独立数据库账号，还需授权：
-- CREATE USER 'homepage'@'localhost' IDENTIFIED BY '你的密码';
-- GRANT ALL ON homepage.* TO 'homepage'@'localhost';
```

### 2. 填写配置

解压 Release 包后，运行 `start.sh`（Linux/macOS）或 `start.bat`（Windows），
首次运行会自动从模板生成 `application-local.yml`，编辑它：

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/homepage?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: homepage
    password: 你的数据库密码

app:
  admin:
    username: 你的管理员用户名
    password: 你的管理员密码
```

- 该文件包含真实凭据，**不要提交到任何仓库**
- 也可以不用此文件，改用环境变量 `DB_USERNAME` / `DB_PASSWORD` / `ADMIN_USERNAME` / `ADMIN_PASSWORD`

### 3. 启动

再次运行 `start.sh` / `start.bat`，访问 **http://localhost:8080**

- 全部数据表在应用首次启动时自动创建
- 配置了管理员账号时，若该用户不存在会自动创建（角色 ADMIN）
- 停止服务：`Ctrl + C`

## 常见问题

- **端口占用**：默认 8080，可在 `application-local.yml` 中加 `server: { port: 8081 }` 修改
- **数据库连接失败**：检查 `url` 中的主机/端口/库名与账号密码
- **登录页报错"剩余尝试次数"**：连续失败 5 次会锁定 15 分钟，稍后再试或重启数据库
