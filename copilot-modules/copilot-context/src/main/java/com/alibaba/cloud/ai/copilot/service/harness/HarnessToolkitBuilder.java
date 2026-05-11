package com.alibaba.cloud.ai.copilot.service.harness;

import com.alibaba.cloud.ai.copilot.config.McpProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessToolkitBuilder {

    private final McpToolInfoMapper mcpToolInfoMapper;
    private final HarnessBuiltinTools harnessBuiltinTools;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public Toolkit build() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(harnessBuiltinTools);

        List<McpToolInfo> enabledTools =
                mcpToolInfoMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpToolInfo>()
                                .eq(McpToolInfo::getStatus, ToolStatus.ENABLED.getValue()));

        for (McpToolInfo tool : enabledTools) {
            try {
                if ("BUILTIN".equalsIgnoreCase(tool.getType())) {
                    continue;
                }
                registerMcpTool(toolkit, tool);
            } catch (Exception e) {
                log.error("注册 Harness 工具失败: {}", tool.getName(), e);
            }
        }

        return toolkit;
    }

    private void registerMcpTool(Toolkit toolkit, McpToolInfo tool) {
        Map<String, Object> cfg = parseConfig(tool.getConfigJson());
        McpClientBuilder builder = McpClientBuilder.create("mcp-" + tool.getId());

        if ("LOCAL".equalsIgnoreCase(tool.getType())) {
            String command = asString(cfg.get("command"));
            List<String> args = asStringList(cfg.get("args"));
            Map<String, String> env = asStringMap(cfg.get("env"));
            builder.stdioTransport(command, args, env);
        } else {
            String baseUrl = asString(cfg.get("baseUrl"));
            builder.sseTransport(baseUrl);
        }

        builder.timeout(Duration.ofSeconds(mcpProperties.getClient().getRequestTimeout()));
        McpClientWrapper wrapper = builder.buildSync();

        toolkit.registration().mcpClient(wrapper).enableTools(List.of(tool.getName())).apply();
        log.info("已注册 Harness MCP 工具: {}", tool.getName());
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 MCP 配置失败，使用空配置: {}", json);
            return Collections.emptyMap();
        }
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, String> asStringMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    e -> String.valueOf(e.getKey()),
                                    e -> String.valueOf(e.getValue())));
        }
        return Collections.emptyMap();
    }
}
