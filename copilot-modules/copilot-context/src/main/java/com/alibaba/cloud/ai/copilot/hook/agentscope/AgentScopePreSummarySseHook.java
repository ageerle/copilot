package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreSummaryEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 摘要生成前 SSE Hook
 * 发送 summary_start 事件到前端
 */
@Slf4j
public class AgentScopePreSummarySseHook implements Hook {

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreSummaryEvent preSummary) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String modelName = preSummary.getModelName();
            Map<String, Object> data = new HashMap<>();
            data.put("model", modelName != null ? modelName : "");
            sseCtx.sendEvent("summary_start", data);

            log.info("[摘要生成开始] model={}", modelName);
        }
        return Mono.just(event);
    }
}
