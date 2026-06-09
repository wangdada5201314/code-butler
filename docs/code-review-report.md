# 🔍 Code Butler 代码审查报告

> **审查日期**：2026-06-09  
> **仓库路径**：`E:\AgentScopeProject\code-butler`  
> **基线版本**：`522d568`（Initial commit v1.0，当前版本 2.0.0-SNAPSHOT）  
> **审查范围**：30 个 Java 源码 + 配置文件

---

## 总结

| 等级 | 数量 | 关键项 |
|------|------|--------|
| 🔴 严重 | 1 | 文件读取接口路径遍历漏洞 |
| 🟡 建议 | 12 | MD5 加密、Session 实体泄漏、CORS、架构解耦 |
| 🟢 优化 | 14 | 代码风格、Druid 密码、README、异常脱敏 |

### 优先级

```
P0 立即修复     → 路径遍历漏洞、Session 脱敏
P1 两周内       → bcrypt 升级、CORS 限制、Cookie 安全、Druid 改密
P2 后续迭代     → 代码风格、门面抽象、缓存优化、文档补充
```

---

## 一、代码质量和规范性

### 🟡 缩进不统一

`CodeScannerService`、`ChatService`、`DocGenerationService` 等文件存在制表符和空格混用。

> 统一 4 空格缩进，添加 `.editorconfig`。

### 🟡 SALT 硬编码

`UserServiceImpl` 中 `SALT = "code-butler"` 写死在源码中，所有环境共用，不符合安全规范。

> 改为 `application.yml` 可配置项，`application-local.yml` 设置实际值。

### 🟢 建议启用静态分析

项目中未发现 Checkstyle / SpotBugs 配置。可在 pom.xml 加入 `maven-checkstyle-plugin`。

### 🟢 `var` 使用不统一

`UserController` 使用了 `var`，但团队规范未明确。建议显式声明类型。

### 🟢 `ChatService.streamChat()` 方法过长

约 90 行，包含 prompt 构建、事件转换、错误处理，建议拆分为独立方法。

---

## 二、潜在 Bug 和安全漏洞

### 🔴 路径遍历漏洞（严重）

**文件**：`CodeButlerController.getFileContext()` + `CodeScannerService.readFileContext()`

`filePath` 参数未做路径校验，攻击者可传入 `../../etc/passwd` 实现任意文件读取。

```java
// 修复：读取前做路径规范化检查
Path root = Paths.get(repoPath).toRealPath();
if (!file.toRealPath().startsWith(root)) {
    throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法文件路径");
}
```

### 🟡 Session 存完整 User 实体

`UserServiceImpl.getLoginUser()` 将含 `userPassword` 的完整 `User` 对象存入 Session。

> 改为存入 `LoginUserVO`（脱敏视图）。

### 🟡 MD5 加密强度不足

`MD5(password + fixedSalt)` 在 GPU 暴力破解面前不可靠。

> 升级为 Spring Security 的 `BCryptPasswordEncoder`：
> ```java
> private final PasswordEncoder encoder = new BCryptPasswordEncoder();
> // 注册: encoder.encode(password)
> // 登录: encoder.matches(password, storedHash)
> ```

### 🟡 CORS 生产风险

`allowed-origins: "*"` 允许任意域名跨域访问。

> 改为具体域名白名单。

### 🟢 Cookie 未设置安全属性

建议显式配置 `HttpOnly`、`Secure`、`SameSite`。

```yaml
server.servlet.session.cookie:
  http-only: true
  secure: true
  same-site: strict
```

### 🟢 Druid 监控密码硬编码

`application.yml` 中 `login-password: admin`，生产环境必须改。

### 🟢 500 异常可能泄漏信息

`ex.getMessage()` 可能含敏感路径。改为返回通用消息，详情仅记日志。

---

## 三、性能优化

### 🟡 文件扫描缓存 TTL 过短

30 秒 TTL 对稳定仓库过于保守，建议增大到 60-120 秒。

### 🟡 `getLoginUser()` 每次查库

每个请求都从 Session 读 ID 再查 DB，高频场景压力大。建议添加本地缓存（Caffeine，5 分钟 TTL）。

### 🟢 缓存联动缺失

Git diff 检测到变更后未主动清除文件扫描缓存。

### 🟢 Agent 超时配置偏大

`call-timeout-seconds: 120` 对单次审查偏大，可考虑根据操作类型动态设置。

---

## 四、架构改进

### 🟡 缺少仓储层抽象

`CodeScannerService` 和 `GitService` 职责混合了数据获取和业务编排。建议提取独立的 `FileScanner` 组件。

### 🟡 SSE 长连接 Session 超时

Chat SSE 端点已有 `@AuthCheck`，但需确认 Session 过期时 SSE 流能否正确中断并返回错误。

### 🟡 AI 模型耦合度高

`ChatService`、`CodeReviewService`、`DocGenerationService` 都直接依赖 `HarnessAgent`。建议引入 `AiAgentService` 门面接口，隔离模型实现。

```java
public interface AiAgentService {
    Msg call(UserMessage msg, RuntimeContext ctx);
    Flux<AgentEvent> stream(UserMessage msg, RuntimeContext ctx);
}
```

### 🟢 角色常量重复

`UserConstant` 和 `UserRoleEnum` 存在重复定义，建议统一走枚举。

### 🟢 建表 SQL 纳入版本管理

`sql/create_table.sql` 已存在，确认已加入 Git。

---

## 五、文档完善

### 🟡 README 不完整

建议补充：项目简介、快速开始、配置说明、API 文档、测试指南。

### 🟢 API 注释缺失

部分 DTO 和 Controller 方法缺少 `@param` / `@return` Javadoc。
