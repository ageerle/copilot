# HarnessAgent 源码级深度解析

## 1. 文档目的

基于 `agentscope-harness` 源码阅读，梳理 `HarnessAgent` 的核心运行机制，并映射到当前仓库的集成选型。

## 2. 核心定位

`HarnessAgent` 是围绕 `ReActAgent` 的**运行时增强层**，并非替代推理内核。

- 核心类：`io.agentscope.harness.agent.HarnessAgent`
- 内部委托：`ReActAgent delegate`（真正的推理引擎）
- 核心价值：
  - 运行时上下文绑定（`userId`、`sessionId`）
  - 工作区与文件系统抽象
  - 基于 Hook 的生命周期扩展
  - 会话持久化、记忆维护、上下文压缩与溢出恢复
  - 子 Agent 与后台任务编排

> **设计要点**：Harness 不替代 ReAct 的推理能力，而是通过组合和 Hook 注入，为生产环境提供可观测、可恢复、可治理的运行态能力。

## 3. 调用生命周期

### 3.1 入口链路（call / stream）

`call(...)` 与 `stream(...)` 遵循相同的前置绑定流程：

1. 绑定 `RuntimeContext`（注入 userId / sessionId）
2. 补全缺失的 `Session` / `SessionKey`
3. 尝试通过 `delegate.loadIfExists` 从 store 恢复历史会话
4. 调用 `delegate.call` 或 `delegate.stream` 进入 ReAct 推理循环

> **关键点**：Harness 的状态管理、上下文注入、文件系统切换等行为，对 call 和 stream 两种调用方式一致生效。

### 3.2 上下文溢出恢复（Overflow Fallback）

当模型上下文超过 token 限制时，`HarnessAgent` 会自动触发强制压缩并重试：

- 溢出检测：匹配常见 token 超限错误文案
- 恢复路径：构造 `CompactionConfig(trigger=1)` 强制压缩，然后重试一次

> **生产意义**：长会话场景下，单次溢出不再直接导致失败，而是自动压缩后继续运行。

## 4. Hook 管线（核心工程化手段）

Harness 的大部分能力通过 Hook 注入 ReAct 生命周期。

| Hook 名称 | 优先级 | 触发时机 | 职责 |
|-----------|--------|----------|------|
| `CompactionHook` | 10 | 推理前 | 按策略压缩历史消息，替换上下文 |
| `ToolResultEvictionHook` | 50 | 工具执行后 | 将超大工具输出外置到文件系统，上下文仅保留摘要 |
| `WorkspaceContextHook` | 900 | 推理前 | 注入工作区知识/记忆/会话上下文，按 token 预算裁剪 |
| `MemoryFlushHook` | — | 会话中 | 从消息片段提取可长期记忆项 |
| `MemoryMaintenanceHook` | — | 会话后 | 将日记层汇总去重到 `MEMORY.md` |
| `SessionPersistenceHook` | 900 | 成功/异常后 | 自动保存会话到 store，失败仅日志告警 |
| `SandboxLifecycleHook` | 50 | 调用前/后/异常 | 获取、持久化、释放沙箱生命周期 |
| `SubagentsHook` | 80 | 构建时 | 注册子 Agent 工具与任务工具，注入使用说明 |
| `AgentTraceHook` | — | 全链路 | 可选的推理与工具执行日志追踪 |

> **互补关系**：`CompactionHook` 处理"累计上下文过长"，`ToolResultEvictionHook` 处理"单次工具输出过大"，两者组合才能保证长期运行不爆上下文。

## 5. 文件系统模式

`HarnessAgent` 支持三种主要的文件系统操作模式，构建阶段三选一，存在显式冲突校验。

### 5.1 本地模式（Local Mode）

- 后端实现：`LocalFilesystemWithShell`
- 特性：
  - 本地工作区 I/O
  - 支持 Shell 命令执行（含超时与输出大小限制）
  - 适合本地开发环境

> **适用场景**：单机部署、开发调试、不需要多副本共享的场景。

### 5.2 远程模式（Remote Mode）

- 后端实现：`RemoteFilesystem`，通常由 `CompositeFilesystem` 包装
- 特性：
  - 基于 `BaseStore` 的共享存储，多副本一致性
  - **设计上不包含 Shell 执行能力**（安全隔离考量）
  - 按 namespace 路由到不同存储后端
  - 文本 read 支持 offset/limit，write 为"新建语义"（已存在则失败）

> **适用场景**：集群部署、需要多实例共享工作区的场景。

### 5.3 沙箱模式（Sandbox Mode）

- 后端实现：沙箱后端文件系统 + 生命周期管理器
- 特性：
  - 隔离的执行环境
  - 通过 state store 恢复/持久化会话状态
  - Hook 自动管理 acquire/release 生命周期

> **适用场景**：需要强隔离的执行环境，如多租户、不可信代码执行。

### 5.4 CompositeFilesystem 组合文件系统

- 按路径前缀最长匹配路由到不同后端
- 未命中时走默认后端
- 根目录 listing/grep 会合并各路由结果并重映射路径
- 远程模式下，`MEMORY.md`、`memory/`、`agents/<agentId>/sessions/` 默认路由到共享远程存储

## 6. 存储与工作区路径

`WorkspaceManager` 采用混合读取与统一写入的设计：

- **读取优先级**：filesystem 视图优先 → 本地 workspace 回退
- **写入路径**：统一通过 filesystem 抽象
- **列表路径**：合并 filesystem 与本地结果并去重

> **设计意图**：避免分布式部署中本地直写导致的多副本不一致问题。

## 7. 记忆架构

记忆处理采用分层机制：

1. **`MemoryFlushManager`（抽取层）**
   - 从最近消息中提取可长期记忆候选项
   - 追加到日期维度的记忆日志（如 `memory/YYYY-MM-DD.md`）
2. **`MemoryConsolidator`（策展层）**
   - 将日记层汇总去重到 `MEMORY.md`
3. **`ConversationCompactor`（压缩层）**
   - 按策略压缩会话历史（可按消息数或 token 数触发）
   - 支持 `keepMessages/keepTokens`、压缩前刷盘/卸载等配置
4. **`ToolResultEvictionHook`（驱逐层）**
   - 将超大工具结果移出上下文

> **架构关系**：记忆系统是"原始增量日志 + 全局精炼记忆"的双层结构。Compaction 与 Eviction 是互补的上下文控制手段。

## 8. 子 Agent 与任务编排

子 Agent 能力由以下组件协同提供：

| 组件 | 职责 |
|------|------|
| `SubagentsHook` | 注册子 Agent 工具与任务工具到 Hook 管线 |
| `AgentSpawnTool` | 提供 `agent_spawn`、`agent_send`、`agent_list` |
| `TaskTool` | 提供 `task_output`、`task_cancel`、`task_list` |
| `DefaultTaskRepository` | 内存 + `CompletableFuture` + daemon 线程池实现异步任务 |
| `AgentSpecLoader` | 从 markdown 文件加载子 Agent 规格（YAML front matter + body prompt） |

> **关键能力**：子 Agent 不只是简单"再起一个 agent"，默认就包含异步任务治理（`timeout_seconds=0` 返回 `task_id`，后续通过 task 工具查询/取消）。

## 9. 与当前仓库的映射关系

当前仓库的 HarnessAgent 集成与 Harness 设计对齐如下：

| Harness 设计 | 当前仓库对应实现 |
|-------------|-----------------|
| 聊天执行迁移至 HarnessAgent | `ChatServiceImpl` 调用 `HarnessAgent` |
| 内置工具统一接入 | `HarnessBuiltinTools` 集成 memory/knowledge/filesystem 工具 |
| 内置工具权威清单 | `HarnessBuiltinToolCatalog` 作为启用判断的事实源 |
| DB 同步与禁用策略 | `SystemToolInitializer` 按清单自动禁用未接入工具 |
| Toolkit 构建去旧化 | `HarnessToolkitBuilder` 移除旧 `ToolCallback` 依赖 |
| 文件系统行为配置化 | `app.harness.filesystem.*` 配置项（mode/timeout/output等） |
| 分布式存储适配 | `HarnessDatabaseStoreAdapter` 实现 `BaseStore` 接口 |

## 10. 部署建议

按部署拓扑显式映射配置模板：

### local-dev（本地开发）
- `mode=local`
- Shell 启用，配严格的超时与输出大小限制

### cluster（集群部署）
- `mode=remote`
- 必须配置分布式 session/store

### isolated（隔离执行）
- `mode=sandbox`
- 配置沙箱 state store 与快照策略

## 11. 运行时检查清单

服务启动后，建议逐项验证：

1. 启动日志显示应用与端口就绪（`Tomcat started on port ...`）
2. 登录端点正常返回 token
3. 聊天 SSE 正确发射 `conversation-id` 与 `complete` 事件
4. 数据库中内置工具状态与 `HarnessBuiltinToolCatalog` 一致
5. 超大工具输出被驱逐到文件系统，而非膨胀上下文
6. 会话持久化在后续调用中能正确加载历史

## 12. 已知待完善项

- 真实模型内容流式回包需要有效的上游模型凭证（当前为测试 key）
- 可选收尾：清理不再引用的非 Harness 遗留工具类
