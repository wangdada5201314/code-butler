# Code Butler

代码仓库智能管家 —— 基于 AgentScope 2.0 + Spring Boot 3.3 的 AI 代码助手

## 技术栈

| 组件 | 版本/说明 |
|------|----------|
| JDK | 21（编译目标 17，兼容 JDK 17+） |
| Spring Boot | 3.3.5 |
| AgentScope | 2.0.0-RC1 (Harness) |
| 默认模型 | DashScope 通义千问 (qwen-plus) |
| 参数校验 | Jakarta Validation |
| 健康检查 | Spring Boot Actuator |
| 辅助工具 | Lombok |

## 快速开始

### 1. 环境准备

- **JDK 17+**（推荐 JDK 21）
- **Maven 3.9+**（已配置 `.mvn/settings.xml`，不需要全局 settings.xml）
- 环境变量 `DASHSCOPE_API_KEY`

### 2. 编译并启动

```bash
# 设置 API Key
export DASHSCOPE_API_KEY=your-key

# 编译
./mvnw clean compile

# 启动
./mvnw spring-boot:run
```

### 3. 运行测试

```bash
# 22 个测试用例，覆盖 Controller / Service / 安全校验
./mvnw test
```

### 4. API 示例

```bash
# 健康检查
curl http://localhost:8080/api/code/health

# 代码审查（阻塞式）
curl -X POST "http://localhost:8080/api/code/review?repoPath=E:/my-project"

# 流式问答（SSE）
curl -X POST http://localhost:8080/api/code/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"这个项目的主要功能是什么？","repoPath":"E:/my-project"}'

# 生成文档
curl -X POST "http://localhost:8080/api/code/docs?repoPath=E:/my-project&docType=README"
```

## 项目结构

```
code-butler/
├── pom.xml
├── .mvn/settings.xml                  # Maven 仓库配置
├── .agentscope/workspace/
│   └── AGENTS.md                      # Agent 人格定义
├── src/main/
│   ├── java/com/agent/codebutler/
│   │   ├── CodeButlerApplication.java
│   │   ├── config/
│   │   │   ├── AgentConfig.java       # AgentScope Agent 初始化
│   │   │   └── CorsConfig.java        # 跨域配置
│   │   ├── controller/
│   │   │   └── CodeButlerController.java
│   │   ├── dto/
│   │   │   ├── ApiResponse.java       # 统一响应体 (Lombok)
│   │   │   ├── CodeChatRequest.java   # 请求 DTO + Jakarta 校验
│   │   │   └── CodeReviewResult.java  # 审查结果 (含 CodeIssue)
│   │   ├── handler/
│   │   │   └── GlobalExceptionHandler.java
│   │   └── service/
│   │       ├── CodeScannerService.java
│   │       └── GitService.java
│   └── resources/
│       └── application.yml
└── src/test/
    └── java/com/agent/codebutler/
        ├── CodeButlerApplicationTests.java
        ├── controller/CodeButlerControllerTest.java
        └── service/
            ├── CodeScannerServiceTest.java
            └── GitServiceTest.java
```

## API 接口

所有接口统一返回格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1717766400000
}
```

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| `GET` | `/api/code/health` | 健康检查 | - |
| `POST` | `/api/code/review` | 代码审查（阻塞式） | `repoPath` (必填) |
| `POST` | `/api/code/chat/stream` | 代码问答（SSE） | `repoPath` (必填), `question` (必填) |
| `POST` | `/api/code/docs` | 生成文档 | `repoPath` (必填), `docType` (README/CONTRIBUTING/API) |

## 安全特性

| 措施 | 说明 |
|------|------|
| 路径安全校验 | `validateRepoPath()` 防路径遍历、命令注入（`..` `;` `|` `` ` `` `$()` 等） |
| 参数校验 | Jakarta Validation（`@Validated` + `@NotBlank`），文档类型白名单 |
| 命令超时 | Git 命令 `waitFor(timeout)` + `destroyForcibly` 兜底 |
| Agent 超时 | `.timeout()` 防止 Agent 调用无限阻塞 |
| 统一异常处理 | `GlobalExceptionHandler` 分级处理（400 / 408 / 500） |
| CORS 跨域 | `CorsConfig` + `application.yml` 配置 |
| SSE 安全 | delta 内容换行转义，防止协议破坏 |
| 大仓库防护 | `Files.walk` limit(5000) 防 OOM + 缓存机制 |

## 配置说明

`application.yml` 核心配置项：

```yaml
agentscope:
  model:
    default: dashscope:qwen-plus       # 默认模型
  call-timeout-seconds: 120             # Agent 调用超时

dashscope:
  api-key: ${DASHSCOPE_API_KEY:}        # 通过环境变量注入

git:
  command-timeout-seconds: 30           # Git 命令超时

cors:
  allowed-origins: "*"                  # 跨域白名单
```

## 扩展方向

1. 接入 MCP 工具协议，对接 Git API、GitHub/GitLab
2. 添加 Middleware，注入项目编码规范检查
3. 使用 Plan Mode 实现长任务（全面重构、跨文件重构）
4. 接入 HITL 审批，敏感文件操作需确认
5. 技能沉淀，常见问题模式自动记录为技能
