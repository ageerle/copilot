package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.hook.Hook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralizes AgentScope hook ordering for ReActAgent construction.
 */
@Component
@RequiredArgsConstructor
public class AgentScopeHookRegistry {

    // 业务 hooks
    private final AgentScopeConversationHistoryHook conversationHistoryHook;
    private final AgentScopeLongTermMemoryHook longTermMemoryHook;
    private final AgentScopeKnowledgeContextHook knowledgeContextHook;
    private final AgentScopeConversationSaveHook conversationSaveHook;
    private final AgentScopeMessageTraceHook messageTraceHook;

    // SSE 事件 hooks
    private final AgentScopePreCallSseHook preCallSseHook;
    private final AgentScopePreReasoningSseHook preReasoningSseHook;
    private final AgentScopeReasoningChunkSseHook reasoningChunkSseHook;
    private final AgentScopePostReasoningSseHook postReasoningSseHook;
    private final AgentScopePreActingSseHook preActingSseHook;
    private final AgentScopeActingChunkSseHook actingChunkSseHook;
    private final AgentScopePostActingSseHook postActingSseHook;
    private final AgentScopePreSummarySseHook preSummarySseHook;
    private final AgentScopeSummaryChunkSseHook summaryChunkSseHook;
    private final AgentScopePostSummarySseHook postSummarySseHook;
    private final AgentScopePostCallSseHook postCallSseHook;
    private final AgentScopeErrorSseHook errorSseHook;

    public List<Hook> hooks(boolean memoryEnabled) {
        List<Hook> list = new ArrayList<>();
        // SSE hooks (按 priority 排序)
        list.add(preCallSseHook);           // priority 1
        list.add(preReasoningSseHook);      // priority 5
        list.add(reasoningChunkSseHook);    // priority 6
        list.add(postReasoningSseHook);     // priority 7
        // 业务 hooks
        list.add(conversationHistoryHook);  // priority 10
        list.add(preActingSseHook);         // priority 15
        list.add(actingChunkSseHook);       // priority 16
        list.add(postActingSseHook);        // priority 17
        if (memoryEnabled) {
            list.add(longTermMemoryHook);   // priority 20
        }
        list.add(knowledgeContextHook);     // priority 30
        list.add(preSummarySseHook);        // priority 40
        list.add(summaryChunkSseHook);      // priority 41
        list.add(postSummarySseHook);       // priority 42
        list.add(conversationSaveHook);     // priority 80
        list.add(postCallSseHook);          // priority 85
        list.add(messageTraceHook);         // priority 90
        list.add(errorSseHook);             // priority 99
        return list;
    }

    public List<Hook> runtimeHooks(boolean memoryEnabled) {
        List<Hook> list = new ArrayList<>();
        // SSE hooks (按 priority 排序)
        list.add(preCallSseHook);           // priority 1
        list.add(preReasoningSseHook);      // priority 5
        list.add(reasoningChunkSseHook);    // priority 6
        list.add(postReasoningSseHook);     // priority 7
        // 业务 hooks (不含 conversationSaveHook)
        list.add(conversationHistoryHook);  // priority 10
        list.add(preActingSseHook);         // priority 15
        list.add(actingChunkSseHook);       // priority 16
        list.add(postActingSseHook);        // priority 17
        if (memoryEnabled) {
            list.add(longTermMemoryHook);   // priority 20
        }
        list.add(knowledgeContextHook);     // priority 30
        list.add(preSummarySseHook);        // priority 40
        list.add(summaryChunkSseHook);      // priority 41
        list.add(postSummarySseHook);       // priority 42
        list.add(postCallSseHook);          // priority 85
        list.add(messageTraceHook);         // priority 90
        list.add(errorSseHook);             // priority 99
        return list;
    }
}
