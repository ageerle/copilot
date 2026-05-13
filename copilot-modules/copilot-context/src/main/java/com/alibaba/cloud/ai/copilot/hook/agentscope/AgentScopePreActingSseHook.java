package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用前 SSE Hook
 * 发送 tool_start 事件到前端
 */
@Slf4j
public class AgentScopePreActingSseHook implements Hook {

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent preActing) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            var toolUse = preActing.getToolUse();
            String toolName = toolUse.getName();
            String toolId = toolUse.getId();
            String toolInput = "";

            try {
                Object input = toolUse.getInput();
                if (input != null) {
                    toolInput = sseCtx.getObjectMapper().writeValueAsString(input);
                }
            } catch (Exception e) {
                log.warn("获取工具输入参数失败: {}", e.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("tool", toolName);
            data.put("toolId", toolId != null ? toolId : "");
            data.put("input", sseCtx.truncate(toolInput, 500));
            sseCtx.sendEvent("tool_start", data);

            log.info("[工具调用开始] {} (id={}) - 输入: {}", toolName, toolId, sseCtx.truncate(toolInput, 200));
        }
        return Mono.just(event);
    }
}
