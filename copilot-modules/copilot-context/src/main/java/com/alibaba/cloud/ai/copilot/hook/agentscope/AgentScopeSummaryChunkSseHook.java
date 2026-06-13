package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.SummaryChunkEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 摘要流式 SSE Hook
 * 发送 summary_chunk 事件到前端
 */
@Slf4j
@Component
public class AgentScopeSummaryChunkSseHook implements Hook {

    @Override
    public int priority() {
        return 41;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof SummaryChunkEvent chunkEvent) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String modelName = chunkEvent.getModelName();
            String chunk = "";
            try {
                if (chunkEvent.getIncrementalChunk() != null) {
                    chunk = sseCtx.truncate(chunkEvent.getIncrementalChunk().getTextContent(), 200);
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("model", modelName != null ? modelName : "");
            data.put("chunk", chunk);
            sseCtx.sendEvent("summary_chunk", data);
        }
        return Mono.just(event);
    }
}
