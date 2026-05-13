package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 调用后 SSE Hook
 * 发送 task_complete 事件到前端
 */
@Slf4j
public class AgentScopePostCallSseHook implements Hook {

    @Override
    public int priority() {
        return 85;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostCallEvent postCall) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String agentName = postCall.getAgent().getName();
            String finalMsg = "";
            try {
                Msg msg = postCall.getFinalMessage();
                if (msg != null) {
                    finalMsg = sseCtx.truncate(msg.getTextContent(), 200);
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("agent", agentName);
            data.put("response", finalMsg);
            sseCtx.sendEvent("task_complete", data);

            log.info("[Agent 调用完成] agent={}, 最终消息长度={}", agentName, finalMsg.length());
        }
        return Mono.just(event);
    }
}
