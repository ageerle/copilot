package com.alibaba.cloud.ai.copilot.hook.agentscope;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Bridges request-scoped metadata into AgentScope hooks.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentScopeHookContextHolder {

    private static final ThreadLocal<AgentScopeHookContext> CONTEXT = new ThreadLocal<>();

    public static void set(AgentScopeHookContext context) {
        CONTEXT.set(context);
    }

    public static AgentScopeHookContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
