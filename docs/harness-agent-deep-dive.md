# HarnessAgent Deep Dive (Source-Based)

## 1. Purpose

This document summarizes how `HarnessAgent` works based on source code reading in `agentscope-harness`, and maps those mechanisms to this repository's integration choices.

## 2. Core Positioning

`HarnessAgent` is a runtime enhancement layer around `ReActAgent`, not a replacement reasoning engine.

- Core class: `io.agentscope.harness.agent.HarnessAgent`
- Internal delegate: `ReActAgent delegate`
- Main value:
  - Runtime context binding (`userId`, `sessionId`)
  - Workspace and filesystem abstraction
  - Hook-based lifecycle extensions
  - Session persistence, memory maintenance, compaction, and overflow recovery
  - Subagent and background task orchestration

## 3. Call Lifecycle

### 3.1 Entry

Both `call(...)` and `stream(...)` run runtime binding first:

1. Bind `RuntimeContext`
2. Fill missing `Session` / `SessionKey`
3. Try loading prior session from store via `delegate.loadIfExists`
4. Invoke `delegate.call` or `delegate.stream`

### 3.2 Overflow fallback

On context overflow errors, `HarnessAgent` applies a forced compaction and retries.

- Overflow detection: common token-limit phrases
- Recovery path: force `CompactionConfig` trigger to 1 and retry once

This improves resilience for long sessions.

## 4. Hook Pipeline

Harness behavior is mostly injected via hooks.

- `CompactionHook`: pre-reasoning compaction and message replacement
- `ToolResultEvictionHook`: evict oversized tool outputs to filesystem
- `WorkspaceContextHook`: inject workspace knowledge/memory/session context
- `MemoryFlushHook` + `MemoryMaintenanceHook`: extract and consolidate memory
- `SessionPersistenceHook`: save session on success and error paths
- `SandboxLifecycleHook`: acquire/release/persist sandbox lifecycle
- `SubagentsHook`: register subagent and task tools and usage guidance
- `AgentTraceHook`: optional tracing

## 5. Filesystem Modes

`HarnessAgent` supports three main filesystem operation styles.

### 5.1 Local mode

- Backend: `LocalFilesystemWithShell`
- Characteristics:
  - Local workspace I/O
  - Shell command execution support
  - Time and output size limits available

### 5.2 Remote mode

- Backend: `RemoteFilesystem` usually wrapped by `CompositeFilesystem`
- Characteristics:
  - Shared storage consistency across replicas
  - No shell execution by design
  - Namespace-based store routing

### 5.3 Sandbox mode

- Backend: sandbox-backed filesystem with lifecycle manager
- Characteristics:
  - Isolated execution environment
  - Session state restore/persist via state store
  - Hook-managed acquire/release

## 6. Store and Workspace Pathing

`WorkspaceManager` performs mixed-source reads and unified writes:

- Read priority: filesystem view first, then local workspace fallback
- Write path: goes through filesystem abstraction
- Listing path: merge and de-duplicate

This design avoids local-only writes in distributed deployments.

## 7. Memory Architecture

Memory handling is layered:

1. `MemoryFlushManager`
  - Extract memory candidates from recent messages
  - Append to date-based memory logs
2. `MemoryConsolidator`
  - Consolidate into `MEMORY.md`
3. `ConversationCompactor`
  - Compress session history by policy
4. `ToolResultEvictionHook`
  - Move oversized tool results out of context

Compaction and eviction are complementary controls.

## 8. Subagent and Task Orchestration

Subagent capabilities are provided by:

- `SubagentsHook`
- `AgentSpawnTool` (`agent_spawn`, `agent_send`, `agent_list`)
- `TaskTool` (`task_output`, `task_cancel`, `task_list`)
- `DefaultTaskRepository` (in-memory async tasks via `CompletableFuture`)

Specs can be loaded from markdown files via `AgentSpecLoader` (YAML front matter + body prompt).

## 9. Mapping to This Repository

This repository integration aligns with Harness design:

- Chat execution moved to `HarnessAgent`
- Builtin tools consolidated in `HarnessBuiltinTools`
- Builtin tool source-of-truth in `HarnessBuiltinToolCatalog`
- DB sync and disable policy in `SystemToolInitializer`
- Toolkit build logic cleaned from legacy callback dependency
- Filesystem behavior exposed by `app.harness.filesystem.*`

## 10. Deployment Recommendations

Use explicit profile mapping by topology:

- `local-dev`:
  - `mode=local`
  - shell enabled with strict timeout/output limits
- `cluster`:
  - `mode=remote`
  - distributed session/store required
- `isolated`:
  - `mode=sandbox`
  - sandbox state store and snapshot strategy configured

## 11. Runtime Checklist

After startup, verify:

1. Service startup logs show app and port ready
2. Login endpoint returns token
3. Chat SSE emits `conversation-id` and `complete`
4. Builtin tool status in DB matches `HarnessBuiltinToolCatalog`
5. Oversized tool outputs are evicted instead of inflating context
6. Session persistence loads history on follow-up calls

## 12. Known Gaps for Full E2E

- Real model content streaming still requires valid upstream model credentials
- Optional cleanup remains for non-Harness legacy utilities if no longer referenced
