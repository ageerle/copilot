package com.alibaba.cloud.ai.copilot.service.impl.agent;

import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;

@Component
public class AgentScopeModelFactory {

    public Model create(ModelConfigEntity config) {
        if (config == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        if (isBlank(config.getApiKey())) {
            throw new IllegalArgumentException("模型 API Key 不能为空");
        }
        if (isBlank(config.getModelName())) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .stream(true);

        if (!isBlank(config.getApiUrl())) {
            builder.baseUrl(config.getApiUrl());
        }

        return builder.build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
