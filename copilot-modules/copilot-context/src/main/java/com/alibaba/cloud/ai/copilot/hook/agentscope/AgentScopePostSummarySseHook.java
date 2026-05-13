package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostSummaryEvent;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 摘要生成后 SSE Hook
 * 发送 summary_complete 事件到前端
 */
@Slf4j
public class AgentScopePostSummarySseHook implements Hook {

    @Override
    public int priority() {
        return 42;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostSummaryEvent postSummary) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String summaryContent = "";
            String modelName = postSummary.getModelName();
            try {
                Msg summaryMsg = postSummary.getSummaryMessage();
                if (summaryMsg != null) {
                    String text = summaryMsg.getTextContent();
                    summaryContent = text != null ? text : "";
                }
            } catch (Exception e) {
                log.warn("获取摘要结果失败: {}", e.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("content", sseCtx.truncate(summaryContent, 500));
            data.put("model", modelName != null ? modelName : "");
            data.put("contentLength", summaryContent.length());
            sseCtx.sendEvent("summary_complete", data);

            log.info("[摘要生成完成] model={}, 内容长度={}", modelName, summaryContent.length());
        }
        return Mono.just(event);
    }
}
