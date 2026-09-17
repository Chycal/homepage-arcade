# 账号系统安全架构

## 加密方案

- **密码哈希**: BCrypt (强度因子12)，每次哈希需约0.3秒，有效抵御GPU暴力破解
- **Token签名**: HMAC-SHA512，Access Token 和 Refresh Token 使用独立密钥
- **密钥管理**: 未配置时自动生成高熵随机密钥（每次重启变化，仅适合开发环境；生产环境建议通过 `jwt.access-secret` / `jwt.refresh-secret` 固定）

## 防攻击措施

- **防暴力破解**: 5次失败后锁定15分钟
- **防用户枚举**: 登录失败统一提示 "用户名或密码错误"
- **Token双令牌机制**: Access Token 15分钟 + Refresh Token 7天
- **无状态Session**: JWT无状态认证，不做Session存储

## 账号分级

- **USER**: 普通用户，可玩游戏
- **ADMIN**: 管理员，可访问管理接口

## 初始管理员账号

- 凭据**不写入代码库**，通过环境变量 `ADMIN_USERNAME` / `ADMIN_PASSWORD`
  或 gitignore 掉的 `application-local.yml`（`app.admin.username` / `app.admin.password`）提供
- 应用启动时若该用户不存在则自动创建（角色 ADMIN，密码以 BCrypt 哈希存储）
- 未配置时跳过初始化，仅输出告警日志
