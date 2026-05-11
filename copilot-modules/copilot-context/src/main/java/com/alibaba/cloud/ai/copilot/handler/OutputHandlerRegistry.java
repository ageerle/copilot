package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 输出处理器注册表
 * 管理各种 OutputType 对应的处理器
 */
@Slf4j
@Component
public class OutputHandlerRegistry {

    private final Map<OutputType, OutputHandler> handlers = new HashMap<>();
    private final OutputHandler defaultHandler = new DefaultHandler();

    public OutputHandlerRegistry(OutputHandler modelStreamingHandler,
                                  OutputHandler modelFinishedHandler,
                                  OutputHandler toolFinishedHandler) {
        // 注册各个 Handler
        handlers.put(OutputType.AGENT_MODEL_STREAMING, modelStreamingHandler);
        handlers.put(OutputType.AGENT_MODEL_FINISHED, modelFinishedHandler);
        handlers.put(OutputType.AGENT_TOOL_FINISHED, toolFinishedHandler);
    }

    /**
     * 处理流式输出
     * 根据 OutputType 查找对应的 Handler 并执行
     */
    public void handle(Object output, SseEmitter emitter) {
        try {
            if (!(output instanceof StreamingOutput streamingOutput)) {
                log.debug("【普通输出】非 StreamingOutput 类型: {}", output.getClass().getSimpleName());
                return;
            }

            OutputType type = streamingOutput.getOutputType();
            OutputHandler handler = handlers.getOrDefault(type, defaultHandler);
            handler.handle(streamingOutput, emitter);

        } catch (Exception e) {
            log.error("处理输出失败", e);
        }
    }
}
