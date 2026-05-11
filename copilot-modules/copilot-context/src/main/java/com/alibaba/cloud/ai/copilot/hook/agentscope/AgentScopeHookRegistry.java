package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Centralizes AgentScope hook ordering for ReActAgent construction.
 */
@Component
@RequiredArgsConstructor
public class AgentScopeHookRegistry {

    private final AgentScopeConversationHistoryHook conversationHistoryHook;
    private final AgentScopeLongTermMemoryHook longTermMemoryHook;
    private final AgentScopeKnowledgeContextHook knowledgeContextHook;
    private final AgentScopeConversationSaveHook conversationSaveHook;
    private final AgentScopeMessageTraceHook messageTraceHook;

    public List<Hook> hooks(boolean memoryEnabled) {
        if (memoryEnabled) {
            return List.of(
                    conversationHistoryHook,
                    longTermMemoryHook,
                    knowledgeContextHook,
                    conversationSaveHook,
                    messageTraceHook
            );
        }
        return List.of(
                conversationHistoryHook,
                knowledgeContextHook,
                conversationSaveHook,
                messageTraceHook
        );
    }

    public List<Hook> runtimeHooks(boolean memoryEnabled) {
        if (memoryEnabled) {
            return List.of(
                    conversationHistoryHook,
                    longTermMemoryHook,
                    knowledgeContextHook,
                    messageTraceHook
            );
        }
        return List.of(
                conversationHistoryHook,
                knowledgeContextHook,
                messageTraceHook
        );
    }
}
