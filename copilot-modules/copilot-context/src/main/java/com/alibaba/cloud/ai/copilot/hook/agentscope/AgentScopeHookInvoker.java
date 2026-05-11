package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Bridges AgentScope hooks into the current Spring Graph agent execution path.
 */
@Slf4j
@Component
public class AgentScopeHookInvoker {

    private static final Agent BRIDGE_AGENT = new BridgeAgent();

    public List<Msg> beforeReasoning(List<Hook> hooks, String modelName, String userMessage) {
        PreReasoningEvent event = new PreReasoningEvent(
                BRIDGE_AGENT,
                modelName != null ? modelName : "unknown",
                null,
                List.of(AgentScopeMessageUtils.textMessage(MsgRole.USER, userMessage))
        );
        return invoke(hooks, event).getInputMessages();
    }

    public void afterCall(List<Hook> hooks, String finalText) {
        if (finalText == null || finalText.isBlank()) {
            return;
        }
        PostCallEvent event = new PostCallEvent(
                BRIDGE_AGENT,
                AgentScopeMessageUtils.textMessage(MsgRole.ASSISTANT, finalText)
        );
        invoke(hooks, event);
    }

    private <T extends HookEvent> T invoke(List<Hook> hooks, T event) {
        T current = event;
        for (Hook hook : hooks) {
            try {
                @SuppressWarnings("unchecked")
                T next = (T) hook.onEvent(current).block();
                if (next != null) {
                    current = next;
                }
            } catch (Exception e) {
                log.error("AgentScope hook 执行失败: hook={}, eventType={}",
                        hook.getClass().getSimpleName(), current.getType(), e);
            }
        }
        return current;
    }

    private static final class BridgeAgent implements Agent {

        private final String agentId = UUID.randomUUID().toString();

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public String getName() {
            return "copilot_agent";
        }

        @Override
        public void interrupt() {
        }

        @Override
        public void interrupt(Msg msg) {
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, Class<?> structuredModel) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, JsonNode schema) {
            return Mono.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema) {
            return Flux.empty();
        }

        @Override
        public Mono<Void> observe(Msg msg) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(List<Msg> msgs) {
            return Mono.empty();
        }
    }
}
