<p align="center">
  <img src="https://img.shields.io/badge/version-1.2.0-blue?style=flat-square" alt="v1.2.0" />
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=flat-square&logo=springboot" alt="Spring Boot 3.3" />
  <img src="https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React 18" />
  <img src="https://img.shields.io/badge/AgentScope-2.0-blueviolet?style=flat-square" alt="AgentScope 2.0" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.0" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="MIT License" />
</p>

<h1 align="center">Code Butler</h1>

<p align="center">
  <strong>基于 AgentScope 2.0 + Spring Boot 3.3 构建的 AI 代码助手</strong><br/>
  代码审查 &middot; 智能问答 &middot; 文档生成 &middot; 通用聊天 &mdash; 一站式 AI 开发体验
</p>

---

## 功能亮点

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>代码审查</h3>
      <p>AI 逐行扫描代码仓库，发现潜在 Bug、安全漏洞和性能问题。支持自定义审查偏好（关注点、深度、指令），审查结果按严重度/文件/行号结构化展示。</p>
    </td>
    <td width="50%" valign="top">
      <h3>智能问答</h3>
      <p>基于 SSE 的流式对话，实时分析项目结构与代码逻辑。支持 Markdown 渲染、代码高亮、打字动画，对话上下文跨请求保持。</p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>文档生成</h3>
      <p>一键生成 README、API 文档、CONTRIBUTING 指南、架构说明和 CHANGELOG，代码编辑器风格预览，支持一键复制。</p>
    </td>
    <td width="50%" valign="top">
      <h3>通用聊天</h3>
      <p>不依赖代码仓库的自由 AI 对话。随时提问技术问题、讨论设计方案、学习新技术，内置快捷话题提示。</p>
    </td>
  </tr>
</table>

**更多能力：**

- **用户级 Agent 记忆** &mdash; 每个用户拥有独立的 AI 会话记忆，AI 逐步学习你的偏好和风格
- **审查偏好配置** &mdash; 自定义关注点（安全/性能/架构/命名/可读性）、审查深度和指令，自动融入 AI prompt
- **收藏仓库** &mdash; 收藏常用仓库路径，各功能面板一键选择，免去重复输入
- **操作历史** &mdash; 所有 AI 操作自动记录，支持分页浏览和详情回溯
- **暗色/亮色主题** &mdash; 一键切换，响应式布局适配移动端

## 架构总览

```
┌───────────────────────────────────────────────────────────────┐
│                       Frontend (React 18)                      │
│    Vite + MUI 5 + Tailwind CSS + SSE + Sidebar Navigation     │
│    侧边栏导航 · 用户偏好配置 · 收藏仓库 · 操作历史            │
│                      Port: 5173 (dev)                         │
├───────────────────────────────────────────────────────────────┤
│                    Backend (Spring Boot 3.3.5)                 │
│     Controller → Service → AgentScope Harness Agent           │
│                  ↘ OperationRecordService (异步记录)           │
│                  ↘ UserPreferenceService (偏好融入 prompt)     │
│                      Port: 8080                               │
├─────────────────────────┬─────────────────────────────────────┤
│    AgentScope 2.0       │      Infrastructure (Docker)        │
│    DeepSeek/Qwen/GLM    │      MySQL 8.0  +  Redis 7          │
│    用户级记忆绑定        │      user + operation_record 表     │
│    偏好驱动审查          │      user_preference + favorite_repo│
└─────────────────────────┴─────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 | 版本/说明 |
|------|------|----------|
| **运行时** | JDK | 21（编译目标 17，兼容 JDK 17+） |
| **后端框架** | Spring Boot | 3.3.5 |
| **AI 框架** | AgentScope | 2.0.0-RC1 (Harness Agent) |
| **默认模型** | DeepSeek Chat | 支持通义千问 / GLM 切换 |
| **前端框架** | React + Vite | 18 + 5 |
| **UI 库** | Material UI + Tailwind CSS | MUI 5 + Tailwind 3 |
| **ORM** | MyBatis-Flex | 1.11.1 |
| **数据库** | MySQL | 8.0（Docker） |
| **会话管理** | Spring Session + Redis | Redis 7（Docker），30 天超时 |
| **认证鉴权** | AOP 注解驱动 | `@AuthCheck` 自定义注解 |
| **API 文档** | SpringDoc OpenAPI | 2.6.0 (Swagger UI) |
| **构建工具** | Maven | 3.9+（含 Wrapper） |

## 快速开始

### 1. 环境准备

- **JDK 17+**（推荐 JDK 21）
- **Maven 3.9+**（已配置 `.mvn/settings.xml`，不需要全局 settings.xml）
- **Docker**（用于 MySQL + Redis 基础设施）

### 2. 启动基础设施

```bash
docker compose up -d
```

启动后会自动创建 `code_butler` 数据库并执行建表脚本。

> **已有数据库？** 如果 Docker volume 已存在，初始化脚本不会重复执行。需要手动运行迁移脚本：
> ```bash
> docker exec -i code-butler-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-123456} code_butler < sql/migration_add_operation_record.sql
> docker exec -i code-butler-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-123456} code_butler < sql/migration_add_preferences.sql
> ```

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL 8.0 | 3306 | 数据库 `code_butler` |
| Redis 7 | 6380 | Session 存储 |

### 3. 配置 API Key

创建 `src/main/resources/application-local.yml`（已加入 `.gitignore`）：

```yaml
openai:
  api-key: your-api-key-here
  base-url: https://api.deepseek.com
```

或通过环境变量配置：

```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=https://api.deepseek.com
```

> 也支持 DashScope 通义千问：设置 `DASHSCOPE_API_KEY` 并修改 `AGENTSCOPE_MODEL=dashscope:qwen-plus`

### 4. 编译并启动

```bash
# 编译后端
./mvnw clean compile

# 启动后端
./mvnw spring-boot:run
```

```bash
# 启动前端
cd frontend && npm install && npm run dev
```

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| 前端开发服务器 | http://localhost:5173 |

### 5. 运行测试

```bash
./mvnw test
```

### 预置测试账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `12345678` | 管理员 |
| `user` | `12345678` | 普通用户 |

> 密码存储采用 `MD5(password + salt)` 方式。**生产环境请务必通过环境变量 `APP_PASSWORD_SALT` 设置独立的强盐值。**

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

### 核心功能

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/code/health` | 健康检查 |
| `POST` | `/api/code/review` | 代码审查（阻塞式） |
| `POST` | `/api/code/chat/stream` | 流式问答（SSE） |
| `POST` | `/api/code/chat/general/stream` | 通用聊天（SSE，无需仓库） |
| `POST` | `/api/code/docs` | 生成文档 |
| `GET` | `/api/code/history` | 操作历史（分页） |

### 用户认证

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/user/register` | 用户注册 |
| `POST` | `/api/user/login` | 用户登录 |
| `GET` | `/api/user/get/login` | 获取当前登录用户 |
| `POST` | `/api/user/logout` | 用户登出 |

### 用户偏好 & 收藏

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` / `PUT` | `/api/user/preference` | 获取/更新审查偏好 |
| `GET` | `/api/user/preference/focus-options` | 可选审查关注点 |
| `GET` / `POST` | `/api/user/favorite-repos` | 收藏仓库列表/新增 |
| `DELETE` | `/api/user/favorite-repos/{id}` | 删除收藏仓库 |

> 完整接口文档请启动后端后访问 [Swagger UI](http://localhost:8080/swagger-ui/index.html)。

## 模型供应商

通过 `AgentConfig.java` 动态注册，支持多种模型：

| 供应商 | 模型标识 | 所需环境变量 |
|--------|---------|-------------|
| DeepSeek | `openai:deepseek-chat` | `OPENAI_API_KEY` + `OPENAI_BASE_URL` |
| 通义千问 | `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| 智谱 GLM | `openai:glm-4` | `OPENAI_API_KEY` + `OPENAI_BASE_URL` |

切换模型只需修改 `AGENTSCOPE_MODEL` 环境变量。

## 配置说明

### 多环境配置层级

```
application.yml（主配置，提交 Git）
    └── application-local.yml（本地敏感配置，.gitignore 排除）
        └── 环境变量（最高优先级）
```

### application-local.yml 示例

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6380
  datasource:
    url: jdbc:mysql://localhost:3306/code_butler
    username: root
    password: your-password-here

openai:
  api-key: your-api-key-here
  base-url: https://api.deepseek.com

# 可选：自定义密码盐值（生产环境建议通过环境变量设置）
# app:
#   security:
#     password-salt: your-strong-salt
```

> `application-local.yml` 已加入 `.gitignore`，不会被提交到仓库。所有敏感配置（数据库密码、API Key）请在此文件中管理。

## 安全特性

| 措施 | 说明 |
|------|------|
| 路径安全校验 | `validateRepoPath()` 防路径遍历、命令注入 |
| 参数校验 | Jakarta Validation + 文档类型白名单 |
| 命令超时 | Git 命令 `Process.waitFor(timeout)` + `destroyForcibly` 兜底 |
| Agent 超时 | `.timeout(Duration)` 防止 Agent 调用无限阻塞 |
| AOP 鉴权 | `@AuthCheck(mustRole)` 注解驱动，校验登录状态和角色 |
| Session 安全 | Spring Session + Redis，HttpOnly Cookie，SameSite=Lax |
| 密码存储 | MD5 + 可配置盐值，支持通过环境变量自定义 |
| 异常处理 | `GlobalExceptionHandler` 分级处理（400/401/404/408/500） |
| CORS 跨域 | `CorsConfig` 可配置，支持 credentials |
| SSE 安全 | delta 内容换行转义，防止协议破坏 |
| 大仓库防护 | 5000 文件上限 + 缓存 (30s TTL) |
| 异步操作记录 | `@Async` 记录 AI 操作历史，失败不影响主业务 |

> **生产环境部署建议：** 通过环境变量 `APP_PASSWORD_SALT` 设置强盐值、修改数据库默认密码、启用 HTTPS、关闭 Druid 监控面板或设置独立凭证。

## 项目结构

```
code-butler/
├── pom.xml                              # Maven 构建文件
├── docker-compose.yml                   # MySQL + Redis 编排
├── sql/                                 # 数据库建表 + 迁移脚本
├── src/main/java/com/agent/codebutler/
│   ├── config/                          # AgentScope / CORS / MyBatis-Flex 配置
│   ├── controller/                      # REST API 控制器
│   ├── dto/                             # 请求/响应 DTO
│   ├── service/                         # 业务编排服务
│   │   ├── CodeReviewService            #   代码审查
│   │   ├── ChatService                  #   智能问答
│   │   ├── GeneralChatService           #   通用聊天
│   │   ├── DocGenerationService         #   文档生成
│   │   ├── UserPreferenceService        #   用户偏好
│   │   └── ...
│   ├── model/                           # 实体 / VO / 枚举
│   ├── mapper/                          # MyBatis-Flex Mapper
│   ├── annotation/ + aop/               # 自定义鉴权
│   └── handler/                         # 全局异常处理
├── src/main/resources/
│   ├── application.yml                  # 主配置（提交 Git）
│   └── application-local.yml            # 本地敏感配置（.gitignore）
├── src/test/                            # 单元测试 + 集成测试
└── frontend/                            # React 前端
    └── src/
        ├── components/                  # UI 组件
        │   ├── Sidebar.jsx              #   侧边栏导航
        │   ├── ReviewPanel.jsx          #   代码审查面板
        │   ├── ChatPanel.jsx            #   智能问答面板
        │   ├── GeneralChatPanel.jsx     #   通用聊天面板
        │   ├── DocsPanel.jsx            #   文档生成面板
        │   └── ...
        ├── hooks/useSSE.js              # SSE 流式处理 Hook
        ├── api/client.js                # 统一 API 客户端
        └── stores/useLoginUser.jsx      # 用户状态 Context
```

## 数据模型

<details>
<summary><strong>操作历史 (operation_record)</strong></summary>

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 自增主键 |
| `userId` | BIGINT | 关联 user.id |
| `opType` | VARCHAR(32) | REVIEW / CHAT / DOC |
| `repoPath` | VARCHAR(512) | 仓库路径（通用聊天为空） |
| `input` | TEXT | 用户输入（截断 500 字） |
| `outputSummary` | TEXT | AI 输出摘要（截断 500 字） |
| `status` | VARCHAR(16) | COMPLETED / FAILED / TIMEOUT |
| `durationMs` | INT | 耗时（毫秒） |

</details>

<details>
<summary><strong>用户偏好 (user_preference)</strong></summary>

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 自增主键 |
| `userId` | BIGINT | 关联 user.id（唯一索引） |
| `reviewFocus` | VARCHAR(512) | 审查关注点，逗号分隔 |
| `reviewDepth` | VARCHAR(32) | detailed / standard / concise |
| `customPrompt` | TEXT | 自定义审查指令 |

</details>

<details>
<summary><strong>收藏仓库 (favorite_repo)</strong></summary>

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 自增主键 |
| `userId` | BIGINT | 关联 user.id |
| `repoPath` | VARCHAR(512) | 仓库绝对路径 |
| `repoName` | VARCHAR(128) | 自定义显示名称 |

</details>

## 扩展方向

- [x] **用户偏好配置** &mdash; 自定义审查关注点、深度和指令，AI 自动融入 prompt
- [x] **收藏仓库 + 快捷操作** &mdash; 收藏常用仓库，各面板一键选择
- [x] **通用 AI 聊天** &mdash; 不依赖仓库的自由对话
- [ ] 结构化审查结果（JSON issue 列表，按严重度/文件/行号展示）
- [ ] 用量统计与配额（token 消耗、操作次数、按角色限流）
- [ ] MCP 工具协议对接（Git API、GitHub/GitLab）
- [ ] 编码规范 Middleware 注入
- [ ] Plan Mode 长任务（全面重构、跨文件修改）
- [ ] HITL 审批（敏感文件操作需确认）
- [ ] 多仓库管理 + 仓库对比

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 40000 | 参数错误 |
| 40100 | 未登录 |
| 40101 | 无权限 |
| 50000 | 系统内部异常 |

## License

[MIT](LICENSE)
