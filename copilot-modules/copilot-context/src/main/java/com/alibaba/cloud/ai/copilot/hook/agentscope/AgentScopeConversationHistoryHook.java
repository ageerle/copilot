package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AgentScope hook that restores conversation history before the first reasoning call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeConversationHistoryHook implements Hook {

    private final ChatMessageMapper chatMessageMapper;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent preReasoningEvent) {
            restoreHistory(preReasoningEvent);
        }
        return Mono.just(event);
    }

    private void restoreHistory(PreReasoningEvent event) {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        if (context == null || context.conversationId() == null) {
            return;
        }

        List<Msg> inputMessages = event.getInputMessages();
        boolean isFirstUserRequest = inputMessages.stream()
                .noneMatch(msg -> msg.getRole() == MsgRole.ASSISTANT || AgentScopeMessageUtils.hasToolResult(msg));
        if (!isFirstUserRequest) {
            log.debug("AgentScope 内部消息流，不加载历史: conversationId={}, messageCount={}",
                    context.conversationId(), inputMessages.size());
            return;
        }

        try {
            int keep = appProperties.getConversation().getSummarization().getMessagesToKeep();
            int limit = Math.max(100, keep * 5);
            List<ChatMessageEntity> entities = chatMessageMapper.selectByConversationIdWithPagination(
                    context.conversationId(), 0, limit);
            if (entities.isEmpty()) {
                return;
            }

            Collections.reverse(entities);
            List<Msg> history = validateAndFixToolCallChain(convertToMessages(entities));
            if (!history.isEmpty()) {
                event.setInputMessages(history);
                log.debug("AgentScope 加载会话历史: conversationId={}, historyCount={}, previousCount={}",
                        context.conversationId(), history.size(), inputMessages.size());
            }
        } catch (Exception e) {
            log.error("AgentScope 加载会话历史失败: conversationId={}", context.conversationId(), e);
        }
    }

    private List<Msg> convertToMessages(List<ChatMessageEntity> entities) {
        List<Msg> messages = new ArrayList<>();
        for (ChatMessageEntity entity : entities) {
            try {
                Msg message = switch (entity.getRole() != null ? entity.getRole().toLowerCase() : "") {
                    case "user" -> AgentScopeMessageUtils.textMessage(MsgRole.USER, entity.getContent());
                    case "assistant" -> convertAssistantMessage(entity);
                    case "system" -> AgentScopeMessageUtils.textMessage(MsgRole.SYSTEM, entity.getContent());
                    case "tool" -> convertToolMessage(entity);
                    default -> AgentScopeMessageUtils.textMessage(MsgRole.USER, entity.getContent());
                };
                if (message != null) {
                    messages.add(message);
                }
            } catch (Exception e) {
                log.error("AgentScope 转换历史消息失败: messageId={}, role={}",
                        entity.getMessageId(), entity.getRole(), e);
            }
        }
        return messages;
    }

    private Msg convertAssistantMessage(ChatMessageEntity entity) {
        List<ToolUseBlock> toolUses = new ArrayList<>();
        if (entity.getMetadata() != null && !entity.getMetadata().isBlank()) {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        entity.getMetadata(), new TypeReference<>() {});
                if (Boolean.TRUE.equals(metadata.get("hasToolCalls"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) metadata.get("toolCalls");
                    if (toolCalls != null) {
                        for (Map<String, Object> toolCall : toolCalls) {
                            String id = stringValue(toolCall.get("id"));
                            String name = stringValue(toolCall.get("name"));
                            Object arguments = toolCall.get("arguments");
                            Map<String, Object> input = argumentsToMap(arguments);
                            if (!id.isBlank() && !name.isBlank()) {
                                toolUses.add(ToolUseBlock.builder().id(id).name(name).input(input).build());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AgentScope 解析 assistant tool_calls 失败: messageId={}", entity.getMessageId(), e);
            }
        }

        Msg.Builder builder = Msg.builder().role(MsgRole.ASSISTANT);
        String content = entity.getContent() != null ? entity.getContent() : "";
        if (toolUses.isEmpty()) {
            return builder.textContent(content).build();
        }
        List<io.agentscope.core.message.ContentBlock> blocks = new ArrayList<>();
        if (!content.isBlank()) {
            blocks.add(io.agentscope.core.message.TextBlock.builder().text(content).build());
        }
        blocks.addAll(toolUses);
        return builder.content(blocks).build();
    }

    private Msg convertToolMessage(ChatMessageEntity entity) {
        if (entity.getMetadata() == null || entity.getMetadata().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(entity.getMetadata(), new TypeReference<>() {});
            String toolCallId = stringValue(metadata.get("toolCallId"));
            String toolName = stringValue(metadata.get("toolName"));
            if (toolCallId.isBlank() || toolName.isBlank()) {
                return null;
            }
            ToolResultBlock result = ToolResultBlock.of(
                    toolCallId,
                    toolName,
                    io.agentscope.core.message.TextBlock.builder()
                            .text(entity.getContent() != null ? entity.getContent() : "")
                            .build());
            return Msg.builder().role(MsgRole.TOOL).content(result).build();
        } catch (Exception e) {
            log.error("AgentScope 创建 tool 历史消息失败: messageId={}", entity.getMessageId(), e);
            return null;
        }
    }

    private List<Msg> validateAndFixToolCallChain(List<Msg> messages) {
        List<Msg> fixedMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Msg msg = messages.get(i);
            List<ToolUseBlock> toolUses = msg.getContentBlocks(ToolUseBlock.class);
            if (msg.getRole() != MsgRole.ASSISTANT || toolUses.isEmpty()) {
                fixedMessages.add(msg);
                continue;
            }

            Set<String> expectedIds = new HashSet<>();
            toolUses.forEach(toolUse -> expectedIds.add(toolUse.getId()));
            int j = i + 1;
            while (j < messages.size() && !expectedIds.isEmpty()) {
                for (ToolResultBlock result : messages.get(j).getContentBlocks(ToolResultBlock.class)) {
                    expectedIds.remove(result.getId());
                }
                j++;
            }
            if (expectedIds.isEmpty()) {
                fixedMessages.add(msg);
            } else {
                log.warn("AgentScope 检测到不完整工具调用链，移除 assistant tool_use");
                fixedMessages.add(AgentScopeMessageUtils.textMessage(MsgRole.ASSISTANT, msg.getTextContent()));
            }
        }
        return fixedMessages;
    }

    private Map<String, Object> argumentsToMap(Object arguments) throws Exception {
        if (arguments instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return typedMap;
        }
        if (arguments instanceof String text && !text.isBlank()) {
            return objectMapper.readValue(text, new TypeReference<>() {});
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }
}
