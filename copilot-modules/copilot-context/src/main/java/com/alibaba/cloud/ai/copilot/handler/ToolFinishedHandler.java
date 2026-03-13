package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.domain.dto.StateDataDTO;
import com.alibaba.cloud.ai.copilot.enums.ToolType;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.utils.PathUtils;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工具调用完成处理器
 * 处理 AGENT_TOOL_FINISHED 类型的输出
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolFinishedHandler implements OutputHandler {

    private final SseEventService sseEventService;

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (output.message() instanceof ToolResponseMessage toolResponse) {
                toolResponse.getResponses().forEach(response -> {
                    log.info("【工具结果】 {}: {}",
                            response.name(), response.responseData());
                });
            }
            Object stateData = output.state().data();
            if (stateData != null) {
                StateDataDTO data = objectMapper.convertValue(stateData, StateDataDTO.class);
                List<StateDataDTO.MessageDTO> messages = data.getMessages();

                if (messages != null) {
                    for (StateDataDTO.MessageDTO message : messages) {
                        if ("ASSISTANT".equals(message.getMessageType()) && message.getToolCalls() != null) {
                            message.getToolCalls().forEach(toolCall -> {
                                if (toolCall.getArguments() != null) {
                                    String filePath = toolCall.getArguments().getFilePath();
                                    String content = toolCall.getArguments().getLines();
                                    if (content == null || content.isEmpty()) {
                                        content = toolCall.getArguments().getContent();
                                    }
                                    String toolName = toolCall.getName();
                                    // 根据工具类型发送不同的事件
                                    ToolType toolType = ToolType.fromToolName(toolName);
                                    if (toolType != null) {
                                        sendToolEvent(emitter, toolType, filePath, content);
                                    } else {
                                        log.error("【工具执行完成】 未知工具类型: {}", toolName);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Error] Failed to convert state data to StateDataDTO: {}", e.getMessage(), e);
        }
    }
    /**
     * 根据工具类型发送相应的事件
     *
     * @param emitter SSE 发射器
     * @param toolType 工具类型
     * @param filePath 文件路径（绝对路径或相对路径）
     * @param content 内容
     */
    private void sendToolEvent(SseEmitter emitter, ToolType toolType, String filePath, String content) {
        String messageId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();

        // 将文件路径转换为相对于 workspace 的相对路径
        String relativePath = PathUtils.toRelativePath(filePath);

        Map<String, Object> data = new HashMap<>();
        data.put("event", toolType.getEventName());
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
                "type", toolType.getDataType(),
                "filePath", relativePath,
                "content", content != null ? content : ""
        ));

        try {
            sseEventService.sendSseEvent(emitter, toolType.getEventName(), data);
            log.info("Sent {} event for file: {} (original: {}), operation: {}",
                    toolType.getEventName(), relativePath, filePath, operationId);
        } catch (Exception e) {
            log.error("Error sending {} event for file: {}", toolType.getEventName(), filePath, e);
        }
    }
}
