package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 调用前 SSE Hook
 * 发送 task_start 事件到前端
 */
@Slf4j
public class AgentScopePreCallSseHook implements Hook {

    @Override
    public int priority() {
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreCallEvent preCall) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String agentName = preCall.getAgent().getName();
            List<Msg> inputMessages = (List<Msg>) preCall.getInputMessages();
            int msgCount = inputMessages != null ? inputMessages.size() : 0;
            String lastMsg = "";
            try {
                if (inputMessages != null && !inputMessages.isEmpty()) {
                    Msg last = inputMessages.get(msgCount - 1);
                    lastMsg = sseCtx.truncate(AgentScopeMessageUtils.textContent(last), 100);
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("agent", agentName);
            data.put("memorySize", msgCount);
            data.put("lastMessage", lastMsg);
            sseCtx.sendEvent("task_start", data);

            log.info("[Agent 调用开始] agent={}, 记忆消息数={}", agentName, msgCount);
        }
        return Mono.just(event);
    }
}
