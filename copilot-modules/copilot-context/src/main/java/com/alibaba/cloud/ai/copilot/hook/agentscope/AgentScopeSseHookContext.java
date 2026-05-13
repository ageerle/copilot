package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE hook 共享上下文
 * 用于在 SSE 事件 hook 之间共享 SseEmitter 和工具方法
 */
@Slf4j
public class AgentScopeSseHookContext {

    private volatile SseEmitter emitter;
    private final SseEventService sseEventService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public AgentScopeSseHookContext(SseEmitter emitter, SseEventService sseEventService, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.sseEventService = sseEventService;
        this.objectMapper = objectMapper;
    }

    public void setEmitter(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public SseEmitter getEmitter() {
        return emitter;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void markCompleted() {
        completed.set(true);
    }

    /**
     * 截断字符串
     */
    public String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    /**
     * 发送 SSE 事件，包装为 {"type": eventType, "data": dataMap} 信封格式
     */
    public void sendEvent(String eventType, Map<String, Object> data) {
        if (completed.get()) {
            return;
        }
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", eventType);
            envelope.put("data", data);
            SseEmitter currentEmitter = this.emitter;
            if (currentEmitter != null) {
                sseEventService.sendSseEvent(currentEmitter, eventType, envelope);
            }
        } catch (Exception e) {
            log.debug("发送 SSE 事件失败: {} - {}", eventType, e.getMessage());
        }
    }
}
