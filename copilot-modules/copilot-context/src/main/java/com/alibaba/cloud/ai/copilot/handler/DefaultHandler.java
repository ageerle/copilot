package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 默认处理器
 * 处理其他未明确处理的输出类型
 */
@Slf4j
public class DefaultHandler implements OutputHandler {

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        try {
            log.debug("【其他输出】 类型: {}, 节点: {}",
                    output.getOutputType(), output.node());
        } catch (Exception e) {
            log.error("处理默认输出失败", e);
        }
    }
}
