package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
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
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.sandbox.SandboxDistributedOptions;
import io.agentscope.harness.agent.sandbox.filesystem.DockerFilesystemSpec;
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
    private final ModelConfigService modelConfigService;
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
            RuntimeContext runtimeContext =
                    RuntimeContext.builder()
                            .sessionId(finalConversationId)
                            .userId(String.valueOf(userId))
                            .build();

            Msg userMsg =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(request.getMessage().getContent()).build())
                            .build();

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

    private HarnessAgent buildHarnessAgent(ChatRequest request) {
        ModelConfigEntity model = modelConfigService.getModelEntityById(Long.valueOf(request.getModelConfigId()));
        if (model == null) {
            throw new IllegalArgumentException("未找到模型配置: " + request.getModelConfigId());
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new IllegalStateException("模型已禁用: " + request.getModelConfigId());
        }
        String modelId = toModelId(model);
        String sysPrompt = buildSystemPrompt();

        HarnessAgent.Builder builder =
                HarnessAgent.builder()
                        .name("copilot_agent")
                        .description("Copilot harness agent")
                        .sysPrompt(sysPrompt)
                        .workspace(Path.of(appProperties.getWorkspace().getRootDirectory()))
                        .model(modelId)
                        .toolkit(harnessToolkitBuilder.build())
                        .maxIters(15)
                        .maxContextTokens(
                                appProperties
                                        .getConversation()
                                        .getSummarization()
                                        .getMaxTokensBeforeSummary())
                        .compaction(
                                CompactionConfig.builder()
                                        .triggerMessages(
                                                appProperties
                                                        .getConversation()
                                                        .getSummarization()
                                                        .getMessagesToKeep())
                                        .build())
                        .toolResultEviction(ToolResultEvictionConfig.defaults());

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

    private String toModelId(ModelConfigEntity model) {
        String provider = model.getProvider() == null ? "" : model.getProvider().toLowerCase();
        String name = model.getModelName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (provider.contains("dashscope") || provider.contains("qwen")) {
            return "dashscope:" + name;
        }
        if (provider.contains("openai") || provider.contains("deepseek") || provider.contains("kimi")) {
            return "openai:" + name;
        }
        if (provider.contains("anthropic") || provider.contains("claude")) {
            return "anthropic:" + name;
        }
        if (provider.contains("gemini") || provider.contains("google")) {
            return "gemini:" + name;
        }
        if (provider.contains("ollama")) {
            return "ollama:" + name;
        }
        return "openai:" + name;
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
