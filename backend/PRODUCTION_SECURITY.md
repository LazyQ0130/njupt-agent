# 南邮智答生产安全基线

## 已落实

- 学生端完全匿名：后端签发 UUID 会话标识，前端只在聊天接口携带
  `X-Anonymous-Session-Id`，不包含注册、登录或学生 JWT。
- 会话隔离：会话、回答和反馈均按匿名会话标识校验归属；猜测 ID 无法读取
  其他访客数据。
- 数据生命周期：匿名会话默认保存 90 天，定时任务显式清理
  `message`、`chat_history` 和 `conversation`。
- 管理员认证：`/api/admin/**` 使用 Spring Security 无状态 JWT，仅允许
  `ADMIN` 角色；登录端点单独公开。
- 分布式限流：生产环境使用 Redis + Bucket4j。聊天默认单 IP 60 次/分钟、
  单匿名会话 30 次/分钟；管理员接口 20 次/分钟，管理员登录 5 次/分钟。
- 管理操作审计：上传资料、执行采集、重新索引、运行评测和人工复核均写入
  `operation_log`。
- 上传防护：同时校验大小、扩展名、MIME、文件签名；服务端文件名使用随机
  UUID，规范化落盘路径并阻止路径穿越。
- DeepSeek Key、管理员密钥、数据库密码和 Crawler Token 只从环境变量读取。
  统一异常响应不返回堆栈、上游正文或密钥。
- Crawler 检查 robots.txt，限制官方域名、页面数、深度、请求频率和重试次数；
  Spring 内部入库接口再次验证共享 Token、HTTPS 与域名白名单。
- CORS 使用显式来源列表；Spring Security 启用无状态会话、安全响应头和统一
  401/403 JSON。

## 生产必填配置

以下变量不得留空，也不得使用 `.env.example` 的开发示例值：

```text
ADMIN_USERNAME
ADMIN_PASSWORD_HASH       BCrypt 等强摘要，禁止明文
ADMIN_JWT_SECRET          至少 32 个随机字节
DEEPSEEK_API_KEY
MYSQL_PASSWORD
CRAWLER_SHARED_TOKEN      独立随机密钥
CORS_ALLOWED_ORIGINS      公开站点的精确 HTTPS Origin
RATE_LIMIT_REDIS_URI      内网 Redis URI
```

不要把任何秘密写入 `VITE_*` 变量：这些变量会进入浏览器产物。

## 上线检查

1. 入口只开放 HTTPS，反向代理设置请求体上限、连接/响应超时和 HSTS。
2. MySQL、Redis、Knowledge Service、Crawler Service 与 Chroma 仅在内网访问。
3. 上传目录挂载为独立非执行卷，不通过 Web Server 直接暴露；生产建议追加
   恶意软件扫描。
4. MySQL 使用最小权限账号、TLS、自动备份和可回滚迁移；保持
   `spring.jpa.hibernate.ddl-auto=validate`。
5. Redis 使用 ACL/密码或 TLS，并监控连接失败、内存和淘汰指标。生产配置在
   Redis 不可用时启动失败，避免限流静默降级。
6. 为 DeepSeek 设置日预算与告警；100 题评测会产生最多 100 次模型调用。
7. 发布前运行 `mvn test`、`npm run build`、`npm run test:sites`，并完成匿名
   隔离、后台 401/200、限流 429、上传签名和密钥扫描测试。

## 数据保护说明

- 匿名会话 ID 是随机标识，不应被当作真实身份或用于跨设备追踪。
- 日志中不要记录完整问题正文、JWT、Cookie、API Key 或上传文件内容。
- 公开隐私说明应披露 90 天会话保留期、反馈用途和管理员处理渠道。
- 如需立即删除个人会话，后续可增加“清除本机会话与服务端数据”接口；该能力
  不应要求学生注册。
