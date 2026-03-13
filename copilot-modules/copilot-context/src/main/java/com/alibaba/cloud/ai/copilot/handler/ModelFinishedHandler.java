package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 模型推理完成处理器
 * 处理 AGENT_MODEL_FINISHED 类型的输出，用于检测工具调用请求
 */
@Slf4j
public class ModelFinishedHandler implements OutputHandler {

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        if (output.getOutputType() != OutputType.AGENT_MODEL_FINISHED) {
            return;
        }

        try {
            if (output.message() instanceof AssistantMessage assistantMessage) {
                // 检查是否包含工具调用
                if (assistantMessage.hasToolCalls()) {
                    assistantMessage.getToolCalls().forEach(toolCall -> {
                        log.info("【工具调用】 名称: {}, 参数: {}",
                                toolCall.name(), toolCall.arguments());
                    });
                } else {
                    log.debug("【模型完成】推理结束，无工具调用");
                }
            }
        } catch (Exception e) {
            log.error("处理模型完成事件失败", e);
        }
    }
}
