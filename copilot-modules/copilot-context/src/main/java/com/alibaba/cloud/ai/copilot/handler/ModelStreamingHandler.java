package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 模型推理流式输出处理器
 * 处理 AGENT_MODEL_STREAMING 类型的输出
 */
@Slf4j
public class ModelStreamingHandler implements OutputHandler {

    private final SseEventService sseEventService;

    public ModelStreamingHandler(SseEventService sseEventService) {
        this.sseEventService = sseEventService;
    }

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        if (output.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return;
        }

        try {
            // 获取思考内容（如果有）
            Object reasoningObj = output.message().getMetadata().get("reasoningContent");
            String reasoningContent = reasoningObj != null ? reasoningObj.toString() : "";

            if (StringUtils.isNotEmpty(reasoningContent)) {
                // 思考内容暂不推送，仅记录日志
                log.info("思考内容: {}", reasoningContent);
            } else {
                // 推送模型生成的内容
                String content = output.message().getText();
                if (StringUtils.isNotEmpty(content)) {
                    log.info("模型回复内容: {}", content);
                    sseEventService.sendChatContent(emitter, content);
                }
            }
        } catch (Exception e) {
            log.error("处理模型流式输出失败", e);
        }
    }
}
