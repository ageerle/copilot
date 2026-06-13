package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具流式 SSE Hook
 * 发送 acting_chunk 事件到前端
 */
@Slf4j
@Component
public class AgentScopeActingChunkSseHook implements Hook {

    @Override
    public int priority() {
        return 16;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ActingChunkEvent chunkEvent) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String toolName = "";
            String chunk = "";
            try {
                if (chunkEvent.getToolUse() != null) {
                    toolName = chunkEvent.getToolUse().getName();
                }
                if (chunkEvent.getChunk() != null) {
                    chunk = sseCtx.truncate(chunkEvent.getChunk().toString(), 200);
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("tool", toolName);
            data.put("chunk", chunk);
            sseCtx.sendEvent("acting_chunk", data);
        }
        return Mono.just(event);
    }
}
