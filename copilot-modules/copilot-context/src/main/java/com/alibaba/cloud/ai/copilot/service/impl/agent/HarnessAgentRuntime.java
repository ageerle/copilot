package com.alibaba.cloud.ai.copilot.service.impl.agent;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class HarnessAgentRuntime {

    private final AppProperties appProperties;
    private final AgentScopeModelFactory agentScopeModelFactory;
    private final SseEventService sseEventService;

    public Flux<String> stream(ChatRequest request,
                               String conversationId,
                               Long userId,
                               ModelConfigEntity modelConfig,
                               SseEmitter emitter) {
        Model model = agentScopeModelFactory.create(modelConfig);
        HarnessAgent agent = HarnessAgent.builder()
                .name("copilot_harness_agent")
                .description("Copilot coding agent powered by AgentScope HarnessAgent")
                .sysPrompt(buildSystemPrompt())
                .model(model)
                .workspace(appProperties.getWorkspace().getRootDirectory())
                .build();

        RuntimeContext runtimeContext = RuntimeContext.builder()
                .sessionId(conversationId)
                .userId(userId == null ? null : String.valueOf(userId))
                .put("conversationId", conversationId)
                .put("modelConfigId", request.getModelConfigId())
                .put("enable_preferences", request.getEnablePreferences())
                .put("enable_preference_learning", request.getEnablePreferenceLearning())
                .build();

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.getMessage().getContent())
                .build();

        return agent.stream(userMsg, runtimeContext)
                .filter(event -> event.getMessage() != null)
                .flatMap(event -> mapEvent(event, emitter));
    }

    private Flux<String> mapEvent(io.agentscope.core.agent.Event event, SseEmitter emitter) {
        String content = event.getMessage().getTextContent();
        if (content == null || content.isBlank()) {
            return Flux.empty();
        }

        if (event.getType() == EventType.REASONING) {
            sseEventService.sendChatContent(emitter, content);
            return Flux.just(content);
        }
        if (event.getType() == EventType.TOOL_RESULT) {
            sseEventService.sendThinkingContent(emitter, content);
        }
        return Flux.empty();
    }

    private String buildSystemPrompt() {
        return "【基础约束】\n"
                + "你是编程agent，使用工具在项目根目录内完成编程任务。\n\n"
                + "【前端开发规范 - 必须遵守】\n"
                + "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n"
                + "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n"
                + "【技术栈】\n"
                + "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，"
                + "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。";
    }
}
