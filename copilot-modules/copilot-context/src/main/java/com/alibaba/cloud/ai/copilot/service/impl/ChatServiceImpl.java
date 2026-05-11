package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.service.harness.HarnessToolkitBuilder;
import com.alibaba.cloud.ai.copilot.store.HarnessDatabaseStoreAdapter;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.sandbox.SandboxDistributedOptions;
import io.agentscope.harness.agent.filesystem.spec.DockerFilesystemSpec;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AppProperties appProperties;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final HarnessToolkitBuilder harnessToolkitBuilder;
    private final HarnessDatabaseStoreAdapter harnessDatabaseStoreAdapter;

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        try {
            String conversationId = request.getConversationId();
            Long userId = LoginHelper.getUserId();
            if (conversationId == null || conversationId.isEmpty()) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());
                conversationId = conversationService.createConversation(userId, createRequest);
            }

            final String finalConversationId = conversationId;

            persistUserMessage(finalConversationId, request.getMessage().getContent());
            conversationService.incrementMessageCount(finalConversationId);
            sseEventService.sendConversationId(emitter, finalConversationId);

            HarnessAgent agent = buildHarnessAgent(request);

            // 构建运行时上下文，用于传递单次 Agent 调用的元数据
            // - sessionId: 会话ID，用于隔离不同对话的消息历史和状态
            // - userId: 用户ID，用于多租户场景下的用户隔离和权限控制
            // RuntimeContext 还支持存储临时属性，可在 Hooks 和 Tools 之间共享数据
            RuntimeContext runtimeContext =
                    RuntimeContext.builder()
                            .sessionId(finalConversationId)
                            .userId(String.valueOf(userId))
                            .build();

            Msg userMsg = Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(request.getMessage().getContent()).build())
                            .build();

            // 配置流式输出选项
            // - eventTypes: 订阅的事件类型，ALL 表示接收所有事件（思考过程、工具调用、回复内容等）
            // - incremental: 增量模式，每次只推送新增内容而非累积内容
            // - includeReasoningChunk: 包含推理过程的增量片段（模型思考时的实时输出）
            // - includeReasoningResult: 包含推理过程的最终结果（思考完成后的汇总）
            StreamOptions streamOptions =
                    StreamOptions.builder()
                            .eventTypes(EventType.ALL)
                            .incremental(true)
                            .includeReasoningChunk(true)
                            .includeReasoningResult(true)
                            .build();

            AtomicBoolean completed = new AtomicBoolean(false);
            agent.stream(List.of(userMsg), streamOptions, runtimeContext)
                    .subscribe(
                            event -> handleEvent(emitter, event),
                            error -> {
                                log.error("HarnessAgent execution error", error);
                                if (completed.compareAndSet(false, true)) {
                                    sseEventService.sendComplete(emitter);
                                }
                            },
                            () -> {
                                try {
                                    updateConversationTitleIfNeeded(
                                            finalConversationId,
                                            request.getMessage().getContent(),
                                            userId);
                                } finally {
                                    if (completed.compareAndSet(false, true)) {
                                        sseEventService.sendComplete(emitter);
                                    }
                                }
                            });
        } catch (Exception e) {
            log.error("Unexpected error in harness mode", e);
            sseEventService.sendComplete(emitter);
        }
    }

    // 写死的模型配置
    private static final String MODEL_API_URL = "https://api.lkeap.cloud.tencent.com/plan/v3";
    private static final String MODEL_API_KEY = "sk-tp-0bIGkNrkfcaTegYOOtNsettDpK5EWkJo9n7ug4VHvgzVzfxm";
    private static final String MODEL_NAME = "glm-5";

    /**
     * 构建 HarnessAgent 实例，配置 Agent 的核心行为和资源限制。
     *
     * <p>HarnessAgent 是 ReActAgent 的高级封装，提供工作空间管理、工具集成、
     * 内存压缩、子代理编排等企业级功能。
     */
    private HarnessAgent buildHarnessAgent(ChatRequest request) {
        // 直接使用写死的模型配置，不再从数据库查询
        Model model = OpenAIChatModel.builder()
                .apiKey(MODEL_API_KEY)
                .modelName(MODEL_NAME)
                .baseUrl(MODEL_API_URL)
                .stream(true)
                .build();

        String sysPrompt = buildSystemPrompt();

        // ========== HarnessAgent 核心配置 ==========
        HarnessAgent.Builder builder =
                HarnessAgent.builder()
                        // Agent 基础标识
                        .name("copilot_agent")              // Agent 名称，用于日志和调试
                        .description("Copilot harness agent") // Agent 描述
                        .sysPrompt(sysPrompt)               // 系统提示词，定义 Agent 的角色和行为规则

                        // 工作空间配置 - Agent 操作文件系统的根目录
                        .workspace(Path.of(appProperties.getWorkspace().getRootDirectory()))

                        // 模型配置 - 使用写死的 API 配置创建的模型
                        .model(model)

                        // 工具集 - 包含文件操作、代码执行、MCP 工具等
                        .toolkit(harnessToolkitBuilder.build())

                        // 最大迭代次数 - 防止 Agent 陷入无限循环
                        .maxIters(150)

                        // 上下文窗口限制 - 超过此 token 数触发对话压缩
                        // 用于控制发送给 LLM 的上下文大小，避免超出模型限制
                        .maxContextTokens(
                                appProperties
                                        .getConversation()
                                        .getSummarization()
                                        .getMaxTokensBeforeSummary())

                        // 对话压缩配置 - 当上下文过长时自动摘要历史消息
                        // triggerMessages: 触发压缩的消息数量阈值
                        // 保留最近的 N 条消息不压缩，确保当前对话上下文完整
                        .compaction(
                                CompactionConfig.builder()
                                        .triggerMessages(
                                                appProperties
                                                        .getConversation()
                                                        .getSummarization()
                                                        .getMessagesToKeep())
                                        .build())

                        // 工具结果淘汰配置 - 处理过大的工具返回结果
                        // 将超长的工具输出写入文件系统，用占位符替换，节省上下文空间
                        .toolResultEviction(ToolResultEvictionConfig.defaults());

        // 应用文件系统模式配置（本地/远程/沙箱）
        applyFilesystemMode(builder);
        return builder.build();
    }

    private void applyFilesystemMode(HarnessAgent.Builder builder) {
        AppProperties.Harness.Filesystem fs = appProperties.getHarness().getFilesystem();
        String mode = fs.getMode() == null ? "local" : fs.getMode().toLowerCase();
        switch (mode) {
            case "remote" -> builder.filesystem(
                    new RemoteFilesystemSpec(harnessDatabaseStoreAdapter)
                            .isolationScope(IsolationScope.USER)
                            .anonymousUserId(fs.getAnonymousUserId()));
            case "sandbox" -> {
                DockerFilesystemSpec docker = new DockerFilesystemSpec();
                AppProperties.Harness.Filesystem.Docker dc = fs.getDocker();
                if (dc != null) {
                    if (dc.getImage() != null && !dc.getImage().isBlank()) {
                        docker.image(dc.getImage());
                    }
                    if (dc.getWorkspaceRoot() != null && !dc.getWorkspaceRoot().isBlank()) {
                        docker.workspaceRoot(dc.getWorkspaceRoot());
                    }
                    if (dc.getNetwork() != null && !dc.getNetwork().isBlank()) {
                        docker.network(dc.getNetwork());
                    }
                }
                builder.filesystem(docker)
                        .sandboxDistributed(
                                SandboxDistributedOptions.builder()
                                        .requireDistributed(fs.isSandboxRequireDistributed())
                                        .build());
            }
            default -> builder.filesystem(new LocalFilesystemSpec()
                            .executeTimeoutSeconds(fs.getExecuteTimeoutSeconds())
                            .maxOutputBytes(fs.getMaxOutputBytes()));
        }
    }

    private String buildSystemPrompt() {
        String rootDirectory = appProperties.getWorkspace().getRootDirectory();
        return "【基础约束】\n"
                + "你是编程agent，使用工具在项目根目录（"
                + rootDirectory
                + "）内完成编程任务。\n\n"
                + "【前端开发规范 - 必须遵守】\n"
                + "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n"
                + "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n"
                + "【技术栈】\n"
                + "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，"
                + "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。";
    }

    private void handleEvent(SseEmitter emitter, Event event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String text = event.getMessage().getTextContent();
        if (text == null || text.isBlank()) {
            return;
        }
        if (event.getType() == EventType.REASONING) {
            sseEventService.sendThinkingContent(emitter, text);
            return;
        }
        sseEventService.sendChatContent(emitter, text);
    }

    private void persistUserMessage(String conversationId, String content) {
        ChatMessageEntity userMessageEntity = new ChatMessageEntity();
        userMessageEntity.setConversationId(conversationId);
        userMessageEntity.setMessageId(UUID.randomUUID().toString());
        userMessageEntity.setRole("user");
        userMessageEntity.setContent(content);
        userMessageEntity.setCreatedTime(LocalDateTime.now());
        userMessageEntity.setUpdatedTime(LocalDateTime.now());
        chatMessageMapper.insert(userMessageEntity);
    }

    private void updateConversationTitleIfNeeded(String conversationId, String firstMessage, Long userId) {
        try {
            var conversation = conversationService.getConversation(conversationId);
            if (conversation != null
                    && ("新对话".equals(conversation.getTitle()) || conversation.getTitle() == null)) {
                String title = firstMessage.length() > 50 ? firstMessage.substring(0, 50) + "..." : firstMessage;
                if (userId == null) {
                    return;
                }
                conversationService.updateConversationTitle(conversationId, title, userId);
            }
        } catch (Exception e) {
            log.warn("更新会话标题失败: conversationId={}", conversationId, e);
        }
    }
}
