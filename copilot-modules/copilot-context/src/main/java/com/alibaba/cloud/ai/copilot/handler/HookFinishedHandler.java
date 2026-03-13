package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hook 节点完成处理器
 * 处理 AGENT_HOOK_FINISHED 类型的输出
 */
@Slf4j
public class HookFinishedHandler implements OutputHandler {

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        if (output.getOutputType() != OutputType.AGENT_HOOK_FINISHED) {
            return;
        }
        try {
            log.debug("【Hook完成】 节点: {}", output.node());
            // Hook 通常不产生有价值的输出，可根据需要扩展
        } catch (Exception e) {
            log.error("处理 Hook 完成事件失败", e);
        }
    }
}
