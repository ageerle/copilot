package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeService;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope hook that injects retrieved project knowledge before reasoning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeKnowledgeContextHook implements Hook {

    private static final int MAX_RESULTS = 3;
    private static final int MIN_QUERY_LENGTH = 5;

    private final KnowledgeService knowledgeService;
    private final KnowledgeAvailabilityChecker availabilityChecker;

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent preReasoningEvent) {
            injectKnowledge(preReasoningEvent);
        }
        return Mono.just(event);
    }

    private void injectKnowledge(PreReasoningEvent event) {
        try {
            if (!availabilityChecker.isAvailable()) {
                return;
            }

            List<Msg> messages = event.getInputMessages();
            boolean isToolCallLoop = !messages.isEmpty() && AgentScopeMessageUtils.hasToolResult(messages.get(messages.size() - 1));
            if (isToolCallLoop) {
                return;
            }

            AgentScopeHookContext context = AgentScopeHookContextHolder.get();
            String userId = context != null ? context.userId() : null;
            if (userId == null || userId.isBlank()) {
                log.warn("AgentScope 未找到 userId，跳过知识上下文注入");
                return;
            }

            String userQuery = extractUserQuery(messages);
            if (userQuery == null || userQuery.length() < MIN_QUERY_LENGTH) {
                return;
            }

            List<Document> knowledgeDocs = knowledgeService.search(userId, userQuery, MAX_RESULTS);
            if (knowledgeDocs.isEmpty()) {
                return;
            }

            String knowledgeContext = knowledgeService.formatAsContext(knowledgeDocs);
            if (knowledgeContext == null || knowledgeContext.isBlank()) {
                return;
            }

            Msg contextMessage = AgentScopeMessageUtils.textMessage(
                    MsgRole.SYSTEM,
                    "## 用户项目上下文\n\n"
                            + "以下是从用户知识库中检索到的相关内容，可以帮助你更好地理解用户的项目：\n\n"
                            + knowledgeContext
                            + "\n\n请基于这些上下文信息回答用户的问题。");
            event.setInputMessages(injectContext(messages, contextMessage));
            log.info("AgentScope 已注入知识上下文: userId={}, 知识块数={}, 查询={}",
                    userId, knowledgeDocs.size(), userQuery);
        } catch (Exception e) {
            log.error("AgentScope 知识上下文注入失败", e);
        }
    }

    private String extractUserQuery(List<Msg> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (msg.getRole() == MsgRole.USER) {
                return AgentScopeMessageUtils.textContent(msg);
            }
        }
        return null;
    }

    private List<Msg> injectContext(List<Msg> messages, Msg contextMessage) {
        List<Msg> result = new ArrayList<>();
        boolean injected = false;
        for (Msg msg : messages) {
            result.add(msg);
            if (!injected && msg.getRole() == MsgRole.SYSTEM) {
                result.add(contextMessage);
                injected = true;
            }
        }
        if (!injected) {
            result.add(0, contextMessage);
        }
        return result;
    }
}
