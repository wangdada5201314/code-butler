# Code Butler

代码仓库智能管家 —— 基于 AgentScope 2.0 + Spring Boot 3.3 的 AI 代码助手

![Version](https://img.shields.io/badge/version-2.0.0--SNAPSHOT-blue)

## 架构总览

```
┌─────────────────────────────────────────────────────┐
│                    Frontend (React 18)               │
│          Vite + MUI + Tailwind CSS + SSE             │
│                   Port: 5173 (dev)                   │
├─────────────────────────────────────────────────────┤
│                    Backend (Spring Boot 3.3.5)        │
│  Controller → Service → AgentScope Harness Agent     │
│                   Port: 8080                         │
├────────────────────┬────────────────────────────────┤
│   AgentScope 2.0   │    Infrastructure (Docker)       │
│   DeepSeek/Qwen/GLM│    MySQL 8.0  +  Redis 7        │
└────────────────────┴────────────────────────────────┘
```

## 技术栈

| 组件 | 版本/说明 |
|------|----------|
| **JDK** | 21（编译目标 17，兼容 JDK 17+） |
| **Spring Boot** | 3.3.5 |
| **AgentScope** | 2.0.0-RC1 (Harness Agent) |
| **默认模型** | DeepSeek Chat（支持通义千问/GLM 切换） |
| **前端框架** | React 18 + Vite 5 |
| **UI 库** | Material UI (MUI) 5 + Tailwind CSS 3 |
| **ORM** | MyBatis-Flex 1.11.1 |
| **数据库** | MySQL 8.0（Docker） |
| **Session** | Spring Session + Redis 7（Docker） |
| **认证鉴权** | AOP 注解驱动 `@AuthCheck` |
| **API 文档** | SpringDoc OpenAPI 2.6.0 (Swagger UI) |
| **参数校验** | Jakarta Validation |
| **工具库** | Lombok, Hutool 5.8.43 |
| **健康检查** | Spring Boot Actuator |
| **构建工具** | Maven 3.9+（含 Wrapper） |

## 快速开始

### 1. 环境准备

- **JDK 17+**（推荐 JDK 21）
- **Maven 3.9+**（已配置 `.mvn/settings.xml`，不需要全局 settings.xml）
- **Docker**（用于 MySQL + Redis 基础设施）

### 2. 启动基础设施

```bash
docker compose up -d
```

启动后会自动创建 `code_butler` 数据库并执行 `sql/create_table.sql` 建表。

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL 8.0 | 3306 | root / 123456，数据库 code_butler |
| Redis 7 | 6380 | Session 存储，30 天超时 |

### 3. 配置 API Key

在 `src/main/resources/application-local.yml`（已加入 `.gitignore`）中配置：

```yaml
openai:
  api-key: your-deepseek-api-key
  base-url: https://api.deepseek.com
```

或通过环境变量：

```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=https://api.deepseek.com
```

> 也支持 DashScope 通义千问：设置 `DASHSCOPE_API_KEY` 并修改 `AGENTSCOPE_MODEL=dashscope:qwen-plus`

### 4. 编译并启动后端

```bash
# 编译
./mvnw clean compile

# 启动
./mvnw spring-boot:run
```

后端启动后访问：http://localhost:8080

Swagger UI：http://localhost:8080/swagger-ui/index.html

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器：http://localhost:5173

### 6. 运行测试

```bash
# 22 个测试用例，覆盖 Controller / Service / Config / 安全校验
./mvnw test
```

## 项目结构

```
code-butler/
├── pom.xml                                  # Maven 构建文件
├── docker-compose.yml                       # MySQL 8.0 + Redis 7 编排
├── README.md
├── .gitignore
├── .mvn/
│   └── settings.xml                         # 阿里云 Maven 镜像 + Sonatype 仓库
├── sql/
│   └── create_table.sql                     # user 表 DDL（预置 admin/user 账号）
├── .agentscope/workspace/
│   ├── AGENTS.md                            # Agent 人格定义
│   └── code-reviewer/memory/                # Agent 记忆
├── logs/                                    # 日志输出目录
│
├── src/main/
│   ├── java/com/agent/codebutler/
│   │   ├── CodeButlerApplication.java       # Spring Boot 启动类
│   │   ├── annotation/
│   │   │   └── AuthCheck.java               # @AuthCheck 鉴权注解
│   │   ├── aop/
│   │   │   └── AuthInterceptor.java         # AOP 鉴权切面
│   │   ├── config/
│   │   │   ├── AgentConfig.java             # AgentScope Agent 初始化
│   │   │   ├── CorsConfig.java              # CORS 跨域配置
│   │   │   └── MybatisFlexConfig.java       # MyBatis-Flex Mapper 扫描
│   │   ├── constant/
│   │   │   └── UserConstant.java            # 用户角色常量
│   │   ├── controller/
│   │   │   ├── CodeButlerController.java    # 核心 API（审查/问答/文档）
│   │   │   ├── HealthController.java        # 健康检查
│   │   │   └── UserController.java          # 用户注册/登录/登出
│   │   ├── dto/
│   │   │   ├── ApiResponse.java             # 统一响应体 (Lombok @Builder)
│   │   │   ├── CodeChatRequest.java         # 流式问答请求 DTO
│   │   │   ├── CodeReviewResult.java        # 审查结果（含 CodeIssue 内部类）
│   │   │   ├── DocGenerateResult.java       # 文档生成结果
│   │   │   ├── LoginUserVO.java             # 登录用户视图（脱敏）
│   │   │   ├── UserLoginRequest.java        # 登录请求
│   │   │   └── UserRegisterRequest.java     # 注册请求
│   │   ├── exception/
│   │   │   ├── BusinessException.java       # 业务异常
│   │   │   └── ErrorCode.java               # 错误码枚举
│   │   ├── handler/
│   │   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   │   ├── mapper/
│   │   │   └── UserMapper.java              # MyBatis-Flex Mapper
│   │   ├── model/
│   │   │   └── entity/User.java             # User 实体（雪花ID/逻辑删除）
│   │   └── service/
│   │       ├── ChatService.java             # SSE 流式问答编排
│   │       ├── CodeReviewService.java       # 代码审查编排
│   │       ├── CodeScannerService.java      # 仓库扫描服务（缓存/限流）
│   │       ├── DocGenerationService.java    # 文档生成编排
│   │       ├── GitService.java              # Git 命令安全封装
│   │       ├── UserService.java             # 用户服务接口
│   │       └── impl/UserServiceImpl.java    # 用户服务实现（MD5 加密）
│   └── resources/
│       ├── application.yml                  # 主配置（提交 Git）
│       ├── application-local.yml            # 本地敏感配置（.gitignore）
│       └── logback-spring.xml               # 日志配置
│
├── src/test/
│   ├── java/com/agent/codebutler/
│   │   ├── CodeButlerApplicationTests.java
│   │   ├── config/AgentConfigTest.java
│   │   ├── controller/
│   │   │   ├── CodeButlerControllerTest.java
│   │   │   ├── HealthControllerTest.java
│   │   │   └── UserControllerTest.java
│   │   └── service/
│   │       ├── CodeScannerServiceTest.java
│   │       └── GitServiceTest.java
│   └── resources/
│       └── application-test.yml             # 测试配置（无外部依赖）
│
└── frontend/                                # React 前端
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    ├── index.html
    └── src/
        ├── main.jsx                         # 入口
        ├── App.jsx                          # 根组件（主题切换/用户状态）
        ├── index.css                        # 全局样式 Tailwind + 主题变量
        ├── api/
        │   └── client.js                    # 统一 API 客户端
        ├── components/
        │   ├── Header.jsx                   # 导航栏
        │   ├── HealthCard.jsx               # 健康状态卡片
        │   ├── ReviewPanel.jsx              # 代码审查面板
        │   ├── ChatPanel.jsx                # SSE 流式问答面板
        │   ├── DocsPanel.jsx                # 文档生成面板
        │   └── LoginModal.jsx               # 登录/注册模态框
        ├── hooks/
        │   └── useSSE.js                    # SSE 流式处理 Hook
        └── stores/
            └── useLoginUser.jsx             # 用户状态 Context
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

### 核心功能 API

| 方法 | 路径 | 鉴权 | 说明 | 参数 |
|------|------|------|------|------|
| `GET` | `/api/code/health` | 无 | 健康检查 | - |
| `POST` | `/api/code/review` | @AuthCheck("user") | 代码审查（阻塞式） | `repoPath`（必填） |
| `POST` | `/api/code/chat/stream` | @AuthCheck("user") | 流式问答（SSE） | `repoPath`（必填），`question`（必填） |
| `POST` | `/api/code/docs` | @AuthCheck("user") | 生成文档 | `repoPath`（必填），`docType`（README/CONTRIBUTING/API/ARCHITECTURE/CHANGELOG） |

### 用户认证 API

| 方法 | 路径 | 鉴权 | 说明 | 参数 |
|------|------|------|------|------|
| `POST` | `/api/user/register` | 无 | 用户注册 | `userAccount`，`userPassword`，`checkPassword` |
| `POST` | `/api/user/login` | 无 | 用户登录 | `userAccount`，`userPassword` |
| `GET` | `/api/user/get/login` | @AuthCheck | 获取当前登录用户 | - |
| `POST` | `/api/user/logout` | 无 | 用户登出 | - |

### 预置账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 12345678 | admin |
| user | 12345678 | user |

> 密码存储为 `MD5(password + "code-butler")`。

## 安全特性

| 措施 | 说明 |
|------|------|
| 路径安全校验 | `validateRepoPath()` 防路径遍历、命令注入（`..` `;` `|` `` ` `` `$()` 等） |
| 参数校验 | Jakarta Validation（`@Validated` + `@NotBlank`），文档类型白名单校验 |
| 命令超时 | Git 命令 `Process.waitFor(timeout)` + `destroyForcibly` 兜底 |
| Agent 超时 | `.timeout(Duration.ofSeconds(...))` 防止 Agent 调用无限阻塞 |
| AOP 鉴权 | `@AuthCheck(mustRole="user")` 注解驱动，校验登录状态和角色 |
| Session 安全 | Spring Session + Redis 存储，30 天超时，Session ID Cookie |
| 密码加密 | MD5(password + salt) 存储，不可逆 |
| 统一异常处理 | `GlobalExceptionHandler` 分级处理（400/401/404/408/500） |
| CORS 跨域 | `CorsConfig` + `application.yml` 配置，支持 credentials |
| SSE 安全 | delta 内容换行转义，防止 SSE 协议破坏 |
| 大仓库防护 | 5000 文件上限 + `ConcurrentHashMap` 缓存 (30s TTL) |
| 请求限流 | 仓库扫描缓存防重复扫描 |

## 配置说明

### 多环境配置层级

```
application.yml（主配置，提交 Git）
    └── application-local.yml（本地敏感配置，.gitignore）
        └── 环境变量（最高优先级）
```

### application.yml 核心配置

```yaml
server:
  port: 8080

spring:
  profiles:
    active: local                     # 自动合并 application-local.yml
  session:
    store-type: redis
    timeout: 2592000                  # 30 天

agentscope:
  model:
    default: ${AGENTSCOPE_MODEL:openai:deepseek-chat}
  call-timeout-seconds: 120
  compaction:
    trigger-messages: 30              # 30 条消息后触发记忆压缩
    keep-messages: 10                 # 保留最近 10 条

mybatis-flex:
  configuration:
    map-underscore-to-camel-case: true

git:
  command-timeout-seconds: 30

cors:
  allowed-origins: "*"
```

### application-local.yml（示例）

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6380                     # Docker Redis 映射端口
  datasource:
    url: jdbc:mysql://localhost:3306/code_butler
    username: root
    password: 123456

openai:
  api-key: sk-xxxxxxxxxxxxxxxx
  base-url: https://api.deepseek.com
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 40000 | 参数错误 |
| 40100 | 未登录 |
| 40101 | 无权限 |
| 50000 | 系统内部异常 |

## 模型供应商

通过 `AgentConfig.java` 动态注册，支持三种模型供应商：

| 供应商 | 模型标识 | 环境变量 |
|--------|---------|----------|
| DeepSeek | `openai:deepseek-chat` | `OPENAI_API_KEY` + `OPENAI_BASE_URL` |
| 通义千问 | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| 智谱 GLM | `openai:glm-4` | `OPENAI_API_KEY` + `OPENAI_BASE_URL` |

切换模型只需修改 `AGENTSCOPE_MODEL` 环境变量或 `application-local.yml`。

## 前端功能

| 面板 | 功能 | 技术要点 |
|------|------|----------|
| **健康检查卡片** | 30 秒轮询 `/api/code/health`，渐变边框动画 | fetch 轮询 + 状态驱动 UI |
| **代码审查面板** | 输入仓库路径，表格展示审查结果（严重度/文件/行号/描述） | 严重度统计 Chips |
| **智能问答面板** | SSE 流式对话，Markdown 渲染，打字动画 | `ReadableStream` + 自定义 Markdown 解析器 |
| **文档生成面板** | 选择文档类型，代码编辑器风格预览，一键复制 | MUI Tabs + 语法高亮样式 |
| **登录/注册** | 模态框表单，Session Cookie 自动管理 | Context 状态管理 + 401 自动拦截 |

支持暗色/亮色主题切换，响应式布局。

## 扩展方向

1. 接入 MCP 工具协议，对接 Git API、GitHub/GitLab
2. 添加 Middleware，注入项目编码规范检查
3. 使用 Plan Mode 实现长任务（全面重构、跨文件重构）
4. 接入 HITL 审批，敏感文件操作需确认
5. 技能沉淀，常见问题模式自动记录为技能
6. Agent 记忆持久化到数据库
7. 多仓库管理 + 仓库对比
