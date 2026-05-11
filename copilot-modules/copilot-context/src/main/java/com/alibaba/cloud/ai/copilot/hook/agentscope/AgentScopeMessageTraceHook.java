package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AgentScope hook that traces messages sent to the model.
 */
@Slf4j
@Component
public class AgentScopeMessageTraceHook implements Hook {

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent preReasoningEvent && log.isDebugEnabled()) {
            trace(preReasoningEvent.getInputMessages());
        }
        return Mono.just(event);
    }

    private void trace(List<Msg> messages) {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        String conversationId = context != null ? context.conversationId() : null;
        int totalChars = messages.stream()
                .mapToInt(msg -> AgentScopeMessageUtils.textContent(msg).length())
                .sum();
        String roleSeq = messages.stream()
                .map(AgentScopeMessageUtils::roleOf)
                .collect(Collectors.joining(" -> "));
        boolean hasToolUse = messages.stream().anyMatch(AgentScopeMessageUtils::hasToolUse);
        boolean hasToolResult = messages.stream().anyMatch(AgentScopeMessageUtils::hasToolResult);
        boolean maybeHasSummary = messages.stream()
                .filter(msg -> msg.getRole() == MsgRole.SYSTEM)
                .map(AgentScopeMessageUtils::textContent)
                .map(String::toLowerCase)
                .anyMatch(text -> text.contains("summary") || text.contains("摘要") || text.contains("总结"));

        log.debug("AgentScopeMessageTraceHook: conversationId={}, messageCount={}, totalChars={}, roles=[{}], hasToolUse={}, hasToolResult={}, maybeHasSummarySystemMessage={}",
                conversationId, messages.size(), totalChars, roleSeq, hasToolUse, hasToolResult, maybeHasSummary);
    }
}
