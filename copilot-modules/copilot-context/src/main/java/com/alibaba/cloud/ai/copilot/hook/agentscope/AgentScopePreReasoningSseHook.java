package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 推理前 SSE Hook
 * 发送 reasoning_start 事件到前端
 */
@Slf4j
public class AgentScopePreReasoningSseHook implements Hook {

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent preReasoning) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String modelName = preReasoning.getModelName();
            int inputMsgCount = 0;
            try {
                inputMsgCount = preReasoning.getInputMessages() != null
                        ? preReasoning.getInputMessages().size() : 0;
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("model", modelName != null ? modelName : "");
            data.put("inputMessages", inputMsgCount);
            sseCtx.sendEvent("reasoning_start", data);

            log.info("[推理开始] model={}, 输入消息数={}", modelName, inputMsgCount);
        }
        return Mono.just(event);
    }
}
