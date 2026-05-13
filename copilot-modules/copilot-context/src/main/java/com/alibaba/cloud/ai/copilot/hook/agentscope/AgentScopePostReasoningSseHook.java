package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推理后 SSE Hook
 * 发送 reasoning 事件到前端（包含文本内容和工具调用决策）
 */
@Slf4j
public class AgentScopePostReasoningSseHook implements Hook {

    @Override
    public int priority() {
        return 7;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasoning) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            String modelName = postReasoning.getModelName();
            StringBuilder textContent = new StringBuilder();
            List<Map<String, Object>> toolCalls = new ArrayList<>();

            try {
                Msg reasoningMsg = postReasoning.getReasoningMessage();
                if (reasoningMsg != null && reasoningMsg.getContent() != null) {
                    for (var block : reasoningMsg.getContent()) {
                        if (block instanceof TextBlock tb) {
                            textContent.append(tb.getText());
                        } else if (block instanceof ToolUseBlock toolUse) {
                            Map<String, Object> toolInfo = new HashMap<>();
                            toolInfo.put("name", toolUse.getName());
                            toolInfo.put("id", toolUse.getId());
                            try {
                                toolInfo.put("input", sseCtx.getObjectMapper().writeValueAsString(toolUse.getInput()));
                            } catch (Exception e) {
                                toolInfo.put("input", String.valueOf(toolUse.getInput()));
                            }
                            toolCalls.add(toolInfo);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析推理结果失败: {}", e.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("model", modelName != null ? modelName : "");
            data.put("content", sseCtx.truncate(textContent.toString(), 500));
            data.put("contentLength", textContent.length());
            if (!toolCalls.isEmpty()) {
                data.put("toolCalls", toolCalls);
            }
            sseCtx.sendEvent("reasoning", data);

            log.info("[推理完成] model={}, 文本长度={}, 工具调用数={}",
                    modelName, textContent.length(), toolCalls.size());
            for (var tc : toolCalls) {
                log.info("  -> 决定调用工具: {}(id={})", tc.get("name"), tc.get("id"));
            }
        }
        return Mono.just(event);
    }
}
