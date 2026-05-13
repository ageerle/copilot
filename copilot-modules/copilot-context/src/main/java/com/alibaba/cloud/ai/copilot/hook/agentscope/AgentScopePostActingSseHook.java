package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用后 SSE Hook
 * 发送 tool_end 事件到前端
 */
@Slf4j
public class AgentScopePostActingSseHook implements Hook {

    @Override
    public int priority() {
        return 17;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostActingEvent postActing) {
            AgentScopeSseHookContext sseCtx = AgentScopeSseHookContextHolder.get();
            if (sseCtx == null || sseCtx.isCompleted()) {
                return Mono.just(event);
            }

            var toolUse = postActing.getToolUse();
            if (toolUse == null) {
                return Mono.just(event);
            }

            String toolName = toolUse.getName();
            String toolId = toolUse.getId();

            var toolResult = postActing.getToolResult();
            String result = parseToolResult(toolResult);
            String status = determineStatus(result);

            Map<String, Object> data = new HashMap<>();
            data.put("tool", toolName);
            data.put("toolId", toolId != null ? toolId : "");
            data.put("result", sseCtx.truncate(result, 500));
            data.put("status", status);
            data.put("duration", 0L);
            sseCtx.sendEvent("tool_end", data);

            log.info("[工具调用完成] {} (id={}) - 状态: {}, 结果: {}",
                    toolName, toolId, status, sseCtx.truncate(result, 200));
        }
        return Mono.just(event);
    }

    private String parseToolResult(Object toolResult) {
        if (toolResult == null) return "";

        if (toolResult instanceof ToolResultBlock resultBlock) {
            if (resultBlock.getOutput() == null || resultBlock.getOutput().isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            for (ContentBlock block : resultBlock.getOutput()) {
                if (block instanceof TextBlock textBlock) {
                    parts.add(textBlock.getText());
                } else {
                    parts.add(String.valueOf(block));
                }
            }
            return String.join("\n", parts);
        }

        return toolResult.toString();
    }

    private String determineStatus(String result) {
        if (result == null || result.isEmpty()) {
            return "success";
        }
        String trimmed = result.trim();
        if (trimmed.startsWith("Error:")
                || trimmed.startsWith("[ERROR]")
                || trimmed.startsWith("错误:")
                || trimmed.startsWith("失败:")) {
            return "error";
        }
        return "success";
    }
}
