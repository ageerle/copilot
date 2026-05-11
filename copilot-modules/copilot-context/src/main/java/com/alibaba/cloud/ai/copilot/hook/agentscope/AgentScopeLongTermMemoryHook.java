package com.alibaba.cloud.ai.copilot.hook.agentscope;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.store.PreferenceDeduplicator;
import com.alibaba.cloud.ai.copilot.store.PreferenceInfo;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AgentScope hook that injects and learns long-term memory.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeLongTermMemoryHook implements Hook {

    private final AppProperties appProperties;
    private final DynamicModelService dynamicModelService;
    private final ModelConfigService modelConfigService;
    private final PreferenceDeduplicator preferenceDeduplicator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (!appProperties.getMemory().isEnabled()) {
            return Mono.just(event);
        }
        if (event instanceof PreReasoningEvent preReasoningEvent) {
            injectMemory(preReasoningEvent);
        } else if (event instanceof PostCallEvent) {
            learnPreference();
        }
        return Mono.just(event);
    }

    private void injectMemory(PreReasoningEvent event) {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        if (context == null || !context.enablePreferences() || context.userId() == null || context.userId().isBlank()) {
            return;
        }
        Store store = context.store();
        if (store == null) {
            return;
        }

        try {
            Optional<StoreItem> profileOpt = store.getItem(List.of("user_profiles"), "user_" + context.userId());
            Map<String, Object> profile = profileOpt.map(StoreItem::getValue).orElse(null);
            Optional<StoreItem> preferencesOpt = store.getItem(List.of("user_preferences"), "user_" + context.userId());
            List<Map<String, Object>> enabledPreferences = enabledPreferences(preferencesOpt);
            String userContext = buildUserContext(profile, enabledPreferences);
            if (userContext.isBlank()) {
                return;
            }

            List<Msg> messages = new ArrayList<>(event.getInputMessages());
            int systemIndex = firstSystemIndex(messages);
            if (systemIndex >= 0) {
                messages.set(systemIndex, AgentScopeMessageUtils.appendText(messages.get(systemIndex), "\n\n" + userContext));
            } else {
                messages.add(0, AgentScopeMessageUtils.textMessage(MsgRole.SYSTEM, userContext));
            }
            event.setInputMessages(messages);
            log.debug("AgentScope 加载用户画像和偏好: userId={}, preferencesCount={}",
                    context.userId(), enabledPreferences.size());
        } catch (Exception e) {
            log.error("AgentScope 加载用户画像失败: userId={}", context.userId(), e);
        }
    }

    private void learnPreference() {
        AgentScopeHookContext context = AgentScopeHookContextHolder.get();
        if (context == null
                || !appProperties.getMemory().isPreferenceLearningEnabled()
                || !context.enablePreferenceLearning()
                || context.userId() == null
                || context.userId().isBlank()
                || context.store() == null) {
            return;
        }

        try {
            Store store = context.store();
            if (userDisabledPreferenceLearning(store, context.userId())) {
                return;
            }
            String lastUserText = context.userMessage();
            if (!shouldFallbackLearn(lastUserText)) {
                return;
            }
            Optional<PreferenceInfo> extractedOpt = extractPreferenceByLlm(context, lastUserText);
            if (extractedOpt.isEmpty()) {
                return;
            }

            PreferenceInfo extracted = extractedOpt.get();
            double minConfidence = appProperties.getMemory().getMinConfidence();
            double confidence = extracted.getConfidence() != null ? extracted.getConfidence() : 0.0;
            if (confidence < Math.max(minConfidence, 0.80)) {
                return;
            }

            List<String> namespace = List.of("user_preferences");
            String key = "user_" + context.userId();
            List<PreferenceInfo> existingPreferences = loadPreferences(store, namespace, key);
            PreferenceInfo finalPreference = preferenceDeduplicator.deduplicate(extracted, existingPreferences);
            boolean isNew = existingPreferences.stream()
                    .noneMatch(p -> safeEq(p.getCategory(), finalPreference.getCategory())
                            && safeEq(p.getValue(), finalPreference.getValue()));
            if (isNew) {
                existingPreferences.add(finalPreference);
            } else {
                existingPreferences.replaceAll(p -> safeEq(p.getCategory(), finalPreference.getCategory())
                        && safeEq(p.getValue(), finalPreference.getValue()) ? finalPreference : p);
            }

            List<Map<String, Object>> items = existingPreferences.stream().map(PreferenceInfo::toMap).toList();
            store.putItem(StoreItem.of(namespace, key, Map.of("items", items)));
            log.info("AgentScope 兜底学习用户偏好(默认禁用): userId={}, category={}, value={}, confidence={}, isNew={}",
                    context.userId(), finalPreference.getCategory(), finalPreference.getValue(),
                    finalPreference.getConfidence(), isNew);
        } catch (Exception e) {
            log.error("AgentScope 偏好学习处理失败: userId={}", context.userId(), e);
        }
    }

    private List<Map<String, Object>> enabledPreferences(Optional<StoreItem> preferencesOpt) {
        List<Map<String, Object>> enabled = new ArrayList<>();
        if (preferencesOpt.isEmpty()) {
            return enabled;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allPrefs = (List<Map<String, Object>>) preferencesOpt.get().getValue().get("items");
        if (allPrefs == null) {
            return enabled;
        }
        for (Map<String, Object> pref : allPrefs) {
            Object enabledObj = pref.get("enabled");
            boolean isEnabled = enabledObj instanceof Boolean b ? b
                    : enabledObj != null && Boolean.parseBoolean(enabledObj.toString());
            if (isEnabled) {
                enabled.add(pref);
            }
        }
        return enabled;
    }

    private String buildUserContext(Map<String, Object> profile, List<Map<String, Object>> preferences) {
        StringBuilder contextBuilder = new StringBuilder();
        if (profile != null) {
            appendProfileField(contextBuilder, "用户姓名", profile.get("name"));
            appendProfileField(contextBuilder, "用户语言", profile.get("language"));
        }
        if (!preferences.isEmpty()) {
            contextBuilder.append("用户偏好：\n");
            for (Map<String, Object> pref : preferences) {
                Object category = pref.get("category");
                Object value = pref.get("value");
                if (category != null && value != null) {
                    contextBuilder.append("- ").append(category).append(": ").append(value).append("\n");
                }
            }
        }
        return contextBuilder.toString().trim();
    }

    private void appendProfileField(StringBuilder builder, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            builder.append(label).append("：").append(value).append("\n");
        }
    }

    private int firstSystemIndex(List<Msg> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getRole() == MsgRole.SYSTEM) {
                return i;
            }
        }
        return -1;
    }

    private boolean userDisabledPreferenceLearning(Store store, String userId) {
        Optional<StoreItem> profileOpt = store.getItem(List.of("user_profiles"), "user_" + userId);
        if (profileOpt.isEmpty()) {
            return false;
        }
        Object enabled = profileOpt.get().getValue().get("enablePreferenceLearning");
        return enabled != null && !Boolean.parseBoolean(enabled.toString());
    }

    private List<PreferenceInfo> loadPreferences(Store store, List<String> namespace, String key) {
        List<PreferenceInfo> preferences = new ArrayList<>();
        Optional<StoreItem> existingItemOpt = store.getItem(namespace, key);
        if (existingItemOpt.isEmpty()) {
            return preferences;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) existingItemOpt.get().getValue().get("items");
        if (items != null) {
            for (Map<String, Object> item : items) {
                preferences.add(PreferenceInfo.fromMap(item));
            }
        }
        return preferences;
    }

    private Optional<PreferenceInfo> extractPreferenceByLlm(AgentScopeHookContext context, String userText) {
        try {
            ChatModel chatModel = getFallbackExtractionModel(context);
            if (chatModel == null) {
                return Optional.empty();
            }
            String instruction = """
你是“用户偏好抽取器（Preference Extractor）”。任务：从【单条用户输入】中抽取可能对后续对话有帮助的“可复用偏好/习惯/默认设置”，并输出严格 JSON（不允许任何解释、Markdown、代码块）。

策略（少漏记优先）：
- 宁可多判断一次，也不要轻易漏掉潜在偏好；但要把“不确定”反映到 confidence 上。
- 如果用户表达的是一次性任务约束、临时要求、或与个人偏好无关，则 should_learn=false。
- 最多输出 1 条偏好（选最重要/最可复用的那条）。

字段要求：
- 只能输出 1 个 JSON 对象。
- category 必须是以下之一：programming_language | framework_preference | tool_preference | coding_style | response_style | language_preference | other
- value：偏好值，简短明确（<=40字），不要包含解释。
- confidence：0.0~1.0。

必须输出以下 JSON 格式（字段齐全）：
{
  "should_learn": true|false,
  "category": "other",
  "value": "",
  "confidence": 0.0,
  "reason": ""
}
""";
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(instruction),
                    new UserMessage("用户输入：\n" + userText)
            )));
            String text = response.getResult().getOutput().getText();
            String json = extractFirstJsonObject(text);
            if (json == null) {
                return Optional.empty();
            }
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            boolean shouldLearn = map.get("should_learn") instanceof Boolean b ? b
                    : map.get("should_learn") != null && Boolean.parseBoolean(map.get("should_learn").toString());
            if (!shouldLearn) {
                return Optional.empty();
            }
            String category = map.get("category") != null ? map.get("category").toString().trim() : "";
            String value = map.get("value") != null ? map.get("value").toString().trim() : "";
            Double confidence = parseDouble(map.get("confidence"));
            if (category.isEmpty() || value.isEmpty() || value.length() > 40) {
                return Optional.empty();
            }
            return Optional.of(PreferenceInfo.builder()
                    .category(category)
                    .value(value)
                    .context(userText)
                    .confidence(confidence != null ? confidence : 0.0)
                    .learnedAt(LocalDateTime.now())
                    .usageCount(1)
                    .source("post_process")
                    .enabled(false)
                    .build());
        } catch (Exception e) {
            log.warn("AgentScope LLM 结构化抽取偏好失败（忽略兜底学习）：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private ChatModel getFallbackExtractionModel(AgentScopeHookContext context) {
        if (context.modelConfigId() != null) {
            try {
                return dynamicModelService.getChatModelWithConfigId(context.modelConfigId());
            } catch (Exception e) {
                log.debug("AgentScope 使用 modelConfigId 获取模型失败，id={}, err={}", context.modelConfigId(), e.getMessage());
            }
        }
        try {
            ModelConfigEntity defaultModel = modelConfigService.getAllModelEntities().stream()
                    .filter(m -> m.getEnabled() != null && m.getEnabled())
                    .sorted((a, b) -> {
                        if (a.getSortOrder() != null && b.getSortOrder() != null) {
                            return a.getSortOrder().compareTo(b.getSortOrder());
                        }
                        if (a.getSortOrder() != null) {
                            return -1;
                        }
                        if (b.getSortOrder() != null) {
                            return 1;
                        }
                        return Long.compare(a.getId(), b.getId());
                    })
                    .findFirst()
                    .orElse(null);
            return defaultModel == null ? null : dynamicModelService.getChatModelWithConfigId(String.valueOf(defaultModel.getId()));
        } catch (Exception e) {
            log.warn("AgentScope 获取兜底抽取模型失败: {}", e.getMessage());
            return null;
        }
    }

    private static boolean shouldFallbackLearn(String text) {
        return text != null && !text.trim().isEmpty() && text.trim().length() >= 2;
    }

    private static boolean safeEq(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String extractFirstJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            }
            if (c == '}') {
                depth--;
            }
            if (depth == 0) {
                return text.substring(start, i + 1).trim();
            }
        }
        return null;
    }
}
