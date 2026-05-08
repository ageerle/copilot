package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.service.impl.agent.HarnessAgentRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final HarnessAgentRuntime harnessAgentRuntime;

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        try {
            String conversationId = request.getConversationId();

            if (conversationId == null || conversationId.isEmpty()) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());

                Long userIdLong = LoginHelper.getUserId();
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}, 原因: 请求中未提供conversationId",
                    conversationId, userIdLong);
            } else {
                log.debug("使用现有会话: conversationId={}", conversationId);
            }

            Long userIdLong = LoginHelper.getUserId();
            final String finalConversationId = conversationId;
            final String userMessageContent = request.getMessage().getContent();
            ModelConfigEntity modelConfig = modelConfigMapper.selectById(request.getModelConfigId());
            if (modelConfig == null) {
                throw new IllegalArgumentException("未找到对应的模型配置，id=" + request.getModelConfigId());
            }

            ChatMessageEntity userMessageEntity = new ChatMessageEntity();
            userMessageEntity.setConversationId(finalConversationId);
            userMessageEntity.setMessageId(UUID.randomUUID().toString());
            userMessageEntity.setRole("user");
            userMessageEntity.setContent(userMessageContent);
            userMessageEntity.setCreatedTime(LocalDateTime.now());
            userMessageEntity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(userMessageEntity);

            conversationService.incrementMessageCount(finalConversationId);
            sseEventService.sendConversationId(emitter, finalConversationId);

            AtomicReference<StringBuilder> assistantReply = new AtomicReference<>(new StringBuilder());
            Flux<String> stream = harnessAgentRuntime.stream(
                    request,
                    finalConversationId,
                    userIdLong,
                    modelConfig,
                    emitter);

            stream.subscribe(
                chunk -> assistantReply.get().append(chunk),
                error -> {
                    if (error instanceof WebClientResponseException wcre) {
                        log.error("Agent execution error: status={}, body={}",
                            wcre.getStatusCode(),
                            wcre.getResponseBodyAsString(),
                            wcre);
                    } else {
                        log.error("Agent execution error", error);
                    }
                    sseEventService.sendComplete(emitter);
                },
                () -> {
                    saveAssistantMessage(finalConversationId, assistantReply.get().toString());
                    updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                    sseEventService.sendComplete(emitter);
                }
            );

        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            sseEventService.sendComplete(emitter);
        }
    }
    /**
     * 更新会话标题（如果是新会话且标题为默认值）
     */
    private void updateConversationTitleIfNeeded(String conversationId, String firstMessage, Long userId) {
        try {
            var conversation = conversationService.getConversation(conversationId);
            if (conversation != null &&
                    ("新对话".equals(conversation.getTitle()) || conversation.getTitle() == null)) {
                // 生成标题（取前50个字符）
                String title = firstMessage.length() > 50
                    ? firstMessage.substring(0, 50) + "..."
                    : firstMessage;
                if (userId == null) {
                    log.debug("跳过更新会话标题：userId 为空: conversationId={}", conversationId);
                    return;
                }
                conversationService.updateConversationTitle(conversationId, title, userId);
                log.debug("更新会话标题: conversationId={}, title={}", conversationId, title);
            }
        } catch (IllegalArgumentException e) {
            // 常见原因：异步线程下无法获取/传递正确的登录上下文，或会话不属于当前用户
            log.warn("更新会话标题被拒绝: conversationId={}, reason={}", conversationId, e.getMessage());
        } catch (Exception e) {
            log.error("更新会话标题失败: conversationId={}", conversationId, e);
        }
    }

    private void saveAssistantMessage(String conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        ChatMessageEntity assistantMessageEntity = new ChatMessageEntity();
        assistantMessageEntity.setConversationId(conversationId);
        assistantMessageEntity.setMessageId(UUID.randomUUID().toString());
        assistantMessageEntity.setRole("assistant");
        assistantMessageEntity.setContent(content);
        assistantMessageEntity.setCreatedTime(LocalDateTime.now());
        assistantMessageEntity.setUpdatedTime(LocalDateTime.now());
        chatMessageMapper.insert(assistantMessageEntity);
    }
}
