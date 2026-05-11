package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.graph.store.Store;

/**
 * Per-call context consumed by AgentScope hooks.
 */
public record AgentScopeHookContext(
        String conversationId,
        String userId,
        String userMessage,
        String modelConfigId,
        boolean enablePreferences,
        boolean enablePreferenceLearning,
        Store store
) {
}
