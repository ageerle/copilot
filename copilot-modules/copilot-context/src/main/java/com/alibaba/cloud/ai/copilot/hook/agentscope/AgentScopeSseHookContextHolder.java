package com.alibaba.cloud.ai.copilot.hook.agentscope;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * ThreadLocal holder for AgentScopeSseHookContext.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentScopeSseHookContextHolder {

    private static final ThreadLocal<AgentScopeSseHookContext> CONTEXT = new ThreadLocal<>();

    public static void set(AgentScopeSseHookContext context) {
        CONTEXT.set(context);
    }

    public static AgentScopeSseHookContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
