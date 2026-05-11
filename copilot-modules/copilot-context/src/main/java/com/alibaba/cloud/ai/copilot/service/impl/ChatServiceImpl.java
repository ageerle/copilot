package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.handler.*;
import com.alibaba.cloud.ai.copilot.hook.agentscope.AgentScopeHookContext;
import com.alibaba.cloud.ai.copilot.hook.agentscope.AgentScopeHookContextHolder;
import com.alibaba.cloud.ai.copilot.hook.agentscope.AgentScopeHookInvoker;
import com.alibaba.cloud.ai.copilot.hook.agentscope.AgentScopeHookRegistry;
import com.alibaba.cloud.ai.copilot.hook.agentscope.AgentScopeMessageUtils;
import com.alibaba.cloud.ai.copilot.interceptor.DynamicSystemPromptInterceptor;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolRegistry;
import com.alibaba.cloud.ai.copilot.service.mcp.McpClientManager;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.tools.*;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.EditFileTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.GrepTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.ReadFileTool;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AppProperties appProperties;
    private final DynamicModelService dynamicModelService;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentScopeHookRegistry agentScopeHookRegistry;
    private final AgentScopeHookInvoker agentScopeHookInvoker;
    private final DynamicSystemPromptInterceptor dynamicSystemPromptInterceptor;
    private final McpClientManager mcpClientManager;
    private final BuiltinToolRegistry builtinToolRegistry;
    private final McpToolInfoMapper mcpToolInfoMapper;
    private final DatabaseStore databaseStore;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        try {
            // 1. 获取或创建会话
            String conversationId = request.getConversationId();

            if (conversationId == null || conversationId.isEmpty()) {
                // 创建新会话
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());

                Long userIdLong = LoginHelper.getUserId();
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}, 原因: 请求中未提供conversationId",
                    conversationId, userIdLong);
            } else {
                log.debug("使用现有会话: conversationId={}", conversationId);
            }

            // 2. 获取 ChatModel
            ChatModel chatModel = dynamicModelService.getChatModelWithConfigId(request.getModelConfigId());

            var agentScopeHooks = agentScopeHookRegistry.runtimeHooks(appProperties.getMemory().isEnabled());

            // 5. 构建 Interceptors
            List<ModelInterceptor> interceptors = new ArrayList<>();

            // 5.1 动态系统提示
            interceptors.add(dynamicSystemPromptInterceptor);

            // 6. 加载工具（Milvus 不可用时过滤掉 search_knowledge）
            List<ToolCallback> allTools = loadToolCallback();
            if (!knowledgeAvailabilityChecker.isAvailable()) {
                allTools.removeIf(t -> "search_knowledge".equals(t.getToolDefinition().name()));
                log.info("向量数据库不可用，已移除 search_knowledge 工具");
            }

            log.info("共加载 {} 个工具", allTools.size());

            String rootDirectory = Paths.get(System.getProperty("user.dir") + "/workspace").toString();

            String prompt = "【基础约束】\n" +
                    "你是编程agent，使用工具在项目根目录（" + rootDirectory + "）内完成编程任务。\n\n" +
                    "【前端开发规范 - 必须遵守】\n" +
                    "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n" +
                    "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n" +
                    "【技术栈】\n" +
                    "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，" +
                    "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。";

            // 6.3 构建 Agent
            var agentBuilder = ReactAgent.builder()
                    .name("copilot_agent")
                    .model(chatModel)
                    .systemPrompt(prompt)
                    .interceptors(interceptors.toArray(new ModelInterceptor[0]))
                    .saver(new MemorySaver())
                    .tools(ListDirectoryTool.createListDirectoryToolCallback(ListDirectoryTool.DESCRIPTION),
                            GrepTool.createGrepToolCallback(GrepTool.DESCRIPTION),
                            EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION),
                            ReadFileTool.createReadFileToolCallback(ReadFileTool.DESCRIPTION),
                            WriteLinesTool.createToolCallback(),
                            DeleteFileTool.createToolCallback()
                    );
            ReactAgent agent = agentBuilder.build();

            // 7. 设置会话ID到上下文（供 Hook 和 Interceptor 使用）
            Long userIdLong = LoginHelper.getUserId();
            // 7. 设置会话ID和用户ID到上下文（供 Interceptor 使用）
            RunnableConfig config = RunnableConfig.builder()
                .addMetadata("conversationId", conversationId)
                .addMetadata("user_id", String.valueOf(userIdLong))
                // 供 AgentScope 长期记忆 hook 兜底 LLM 结构化抽取时使用当前会话模型配置
                .addMetadata("model_config_id", request.getModelConfigId()).build();

            // 设置偏好相关开关
            boolean enablePreferences = request.getEnablePreferences() != null
                ? request.getEnablePreferences()
                : true; // 默认启用
            boolean enablePreferenceLearning = request.getEnablePreferenceLearning() != null
                ? request.getEnablePreferenceLearning()
                : true; // 默认启用

            AgentScopeHookContextHolder.set(new AgentScopeHookContext(
                conversationId,
                String.valueOf(userIdLong),
                request.getMessage().getContent(),
                request.getModelConfigId(),
                enablePreferences,
                enablePreferenceLearning,
                appProperties.getMemory().isEnabled() ? databaseStore : null
            ));
            log.debug("AgentScope hooks prepared: count={}", agentScopeHooks.size());

            // 8. 保存用户消息到数据库
            final String finalConversationId = conversationId;
            final String userMessageContent = request.getMessage().getContent();

            ChatMessageEntity userMessageEntity = new ChatMessageEntity();
            userMessageEntity.setConversationId(finalConversationId);
            userMessageEntity.setMessageId(UUID.randomUUID().toString());
            userMessageEntity.setRole("user");
            userMessageEntity.setContent(userMessageContent);
            userMessageEntity.setCreatedTime(LocalDateTime.now());
            userMessageEntity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(userMessageEntity);

            // 9. 增加消息计数
            conversationService.incrementMessageCount(finalConversationId);

            // 10. 发送会话ID到前端（供前端保存并复用）
            sseEventService.sendConversationId(emitter, finalConversationId);

            // 11. 执行 Agent
            String agentInput = AgentScopeMessageUtils.plainTextTranscript(
                    agentScopeHookInvoker.beforeReasoning(agentScopeHooks, chatModel.getClass().getSimpleName(), userMessageContent));
            Flux<NodeOutput> stream = agent.stream(agentInput, config);
            // 创建 Handler Registry
            OutputHandlerRegistry handlerRegistry = createHandlerRegistry();
            stream.subscribe(
                output -> handlerRegistry.handle(output, emitter),
                error -> {
                    if (error instanceof WebClientResponseException wcre) {
                        // 关键：打印下游模型服务返回的错误响应体，便于定位 400 的具体原因
                        log.error("Agent execution error: status={}, body={}",
                            wcre.getStatusCode(),
                            wcre.getResponseBodyAsString(),
                            wcre);
                    } else {
                        log.error("Agent execution error", error);
                    }
                    sseEventService.sendComplete(emitter);
                    AgentScopeHookContextHolder.clear();
                },
                () -> {
                    // 流完成后，更新会话标题（基于首条用户消息）
                    updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                    sseEventService.sendComplete(emitter);
                    AgentScopeHookContextHolder.clear();
                }
            );

        } catch (GraphRunnerException e) {
            log.error("Error in builder mode", e);
            sseEventService.sendComplete(emitter);
            AgentScopeHookContextHolder.clear();
        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            sseEventService.sendComplete(emitter);
            AgentScopeHookContextHolder.clear();
        }
    }

    /**
     * 初始化 Handler Registry
     * 用于处理不同类型的 OutputType
     */
    private OutputHandlerRegistry createHandlerRegistry() {
        return new OutputHandlerRegistry(
                new ModelStreamingHandler(sseEventService),
                new ModelFinishedHandler(),
                new ToolFinishedHandler(sseEventService)
        );
    }

    /**
     * 加载工具
     */
    private List<ToolCallback> loadToolCallback() {
        LambdaQueryWrapper<McpToolInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(McpToolInfo::getStatus, ToolStatus.ENABLED.getValue());
        List<McpToolInfo> enabledTools = mcpToolInfoMapper.selectList(queryWrapper);
        List<ToolCallback> allTools = new ArrayList<>();
        for (McpToolInfo tool : enabledTools) {
            try {
                if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getType())) {
                    // 内置工具 - 从注册表获取
                    ToolCallback callback = builtinToolRegistry.createToolCallback(tool.getName());
                    if (callback != null) {
                        allTools.add(callback);
                        log.debug("加载内置工具: {}", tool.getName());
                    }
                } else {
                    // MCP 工具 (LOCAL/REMOTE) - 从 McpClientManager 获取
                    List<ToolCallback> mcpCallbacks = mcpClientManager.getToolCallbacks(List.of(tool.getId()));
                    allTools.addAll(mcpCallbacks);
                    log.debug("加载 MCP 工具: {}", tool.getName());
                }
            } catch (Exception e) {
                log.error("加载工具失败: {} - {}", tool.getName(), e.getMessage());
                // 继续加载其他工具，不阻断
            }
        }
        return allTools;
    }


    /**
     * 更新会话标题（如果是新会话且标题为默认值）
     */
    private void updateConversationTitleIfNeeded(String conversationId, String firstMessage, Long userId) {
        try {
            var conversation = conversationService.getConversation(conversationId);
            if (conversation != null &&
                    ("新对话".equals(conversation.getTitle()) || conversation.getTitle() == null)) {
                // 生成标题（取前50个字符）
                String title = firstMessage.length() > 50
                    ? firstMessage.substring(0, 50) + "..."
                    : firstMessage;
                if (userId == null) {
                    log.debug("跳过更新会话标题：userId 为空: conversationId={}", conversationId);
                    return;
                }
                conversationService.updateConversationTitle(conversationId, title, userId);
                log.debug("更新会话标题: conversationId={}, title={}", conversationId, title);
            }
        } catch (IllegalArgumentException e) {
            // 常见原因：异步线程下无法获取/传递正确的登录上下文，或会话不属于当前用户
            log.warn("更新会话标题被拒绝: conversationId={}, reason={}", conversationId, e.getMessage());
        } catch (Exception e) {
            log.error("更新会话标题失败: conversationId={}", conversationId, e);
        }
    }
}
