package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 输出处理器接口
 * 定义了处理不同 OutputType 的统一接口
 */
public interface OutputHandler {
    /**
     * 处理流式输出
     *
     * @param output 流式输出对象
     * @param emitter SSE 发送器
     */
    void handle(StreamingOutput output, SseEmitter emitter);
}