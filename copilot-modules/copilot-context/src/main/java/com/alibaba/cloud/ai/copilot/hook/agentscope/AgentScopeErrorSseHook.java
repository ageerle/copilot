package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误监听 SSE Hook
 * 发送 error 事件到前端
 */
@Slf4j
@Component
public class AgentScopeErrorSseHook implements Hook {

    @Override
    public int priority() {
        return 99;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ErrorEvent errorEvent) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            Throwable err = errorEvent.getError();
            String errorMsg = err != null ? err.getMessage() : "未知错误";
            String errClass = err != null ? err.getClass().getSimpleName() : "";
            String stackTrace = "";
            try {
                if (err != null && err.getStackTrace().length > 0) {
                    stackTrace = err.getStackTrace()[0].toString();
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<>();
            data.put("agent", errorEvent.getAgent().getName());
            data.put("message", errorMsg);
            data.put("errorType", errClass);
            data.put("stackTrace", stackTrace);
            sseCtx.sendEvent("error", data);

            log.error("[Agent 错误] agent={}, type={}, message={}",
                    errorEvent.getAgent().getName(), errClass, errorMsg);
        }
        return Mono.just(event);
    }
}
