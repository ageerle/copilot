package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AgentScope hook that persists assistant and tool messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeConversationSaveHook implements Hook {

    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasoningEvent) {
            saveReasoningMessage(postReasoningEvent.getReasoningMessage());
        } else if (event instanceof PostActingEvent postActingEvent) {
            saveToolResult(postActingEvent.getToolResult());
        } else if (event instanceof PostCallEvent postCallEvent) {
            saveFinalMessage(postCallEvent.getFinalMessage());
        }
        return Mono.just(event);
    }

    private void saveReasoningMessage(Msg message) {
        if (message == null || !AgentScopeMessageUtils.hasToolUse(message)) {
            return;
        }
        saveAssistantMessage(message, false);
    }

    private void saveFinalMessage(Msg message) {
        if (message == null || AgentScopeMessageUtils.textContent(message).isBlank()) {
            return;
        }
        saveAssistantMessage(message, true);
    }

    private void saveAssistantMessage(Msg message, boolean finalMessage) {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        if (context == null || context.conversationId() == null) {
            return;
        }
        try {
            ChatMessageEntity entity = baseEntity(context.conversationId(), "assistant");
            entity.setContent(AgentScopeMessageUtils.textContent(message));

            List<ToolUseBlock> toolUses = message.getContentBlocks(ToolUseBlock.class);
            if (!toolUses.isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("hasToolCalls", true);
                metadata.put("toolCalls", toolUses.stream()
                        .map(toolUse -> Map.of(
                                "id", safe(toolUse.getId()),
                                "type", "function",
                                "name", safe(toolUse.getName()),
                                "arguments", toolUse.getInput() != null ? toolUse.getInput() : Map.of()))
                        .toList());
                entity.setMetadata(objectMapper.writeValueAsString(metadata));
            }

            chatMessageMapper.insert(entity);
            log.debug("AgentScope 保存 Assistant 消息: conversationId={}, finalMessage={}, hasToolUses={}",
                    context.conversationId(), finalMessage, !toolUses.isEmpty());
        } catch (Exception e) {
            log.error("AgentScope 保存 Assistant 消息失败", e);
        }
    }

    private void saveToolResult(ToolResultBlock result) {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        if (context == null || context.conversationId() == null || result == null) {
            return;
        }
        try {
            ChatMessageEntity entity = baseEntity(context.conversationId(), "tool");
            entity.setContent(result.getOutput().stream()
                    .map(Object::toString)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse(""));
            Map<String, Object> metadata = new HashMap<>();
            if (result.getId() != null) {
                metadata.put("toolCallId", result.getId());
            }
            if (result.getName() != null) {
                metadata.put("toolName", result.getName());
            }
            entity.setMetadata(objectMapper.writeValueAsString(metadata));
            chatMessageMapper.insert(entity);
            log.debug("AgentScope 保存 Tool 消息: conversationId={}, toolCallId={}",
                    context.conversationId(), result.getId());
        } catch (Exception e) {
            log.error("AgentScope 保存 Tool 消息失败", e);
        }
    }

    private ChatMessageEntity baseEntity(String conversationId, String role) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole(role);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
