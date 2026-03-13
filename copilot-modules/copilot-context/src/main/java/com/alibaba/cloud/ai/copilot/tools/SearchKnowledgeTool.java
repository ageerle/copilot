package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeService;
import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolProvider;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 知识库搜索工具
 * 允许 AI 主动搜索用户的知识库,查找相关代码和文档
 *
 * @author RobustH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool
        implements BiFunction<SearchKnowledgeTool.SearchParams, ToolContext, String>, BuiltinToolProvider {

    private final KnowledgeService knowledgeService;

    public static final String DESCRIPTION = """
            Search the user's knowledge base (codebase and documents) for relevant information.
            Returns matching code snippets, documentation, and file references based on semantic similarity.

            Use this when you need to find specific information in the user's project,
            such as code examples, configuration files, class definitions, or documentation.

            IMPORTANT: The 'query' parameter should describe WHAT you are looking for semantically
            (e.g. 'project introduction', 'user authentication implementation', 'database configuration'),
            NOT include user IDs, folder names, or system identifiers.

            IMPORTANT: Parameters must be a JSON object with the following fields:
            - query (required): The search query describing what you are looking for
            - file_type (optional): Filter by file type - CODE, DOCUMENT, or CONFIG
            - top_k (optional): Number of results to return, default 5

            Example call format:
            {"query": "user authentication implementation", "file_type": "CODE", "top_k": 10}
            """;

    @Override
    public String apply(SearchParams params, ToolContext toolContext) {
        try {
            // 验证参数
            String validationError = validateParams(params);
            if (validationError != null) {
                return "Error: " + validationError;
            }

            // 从 context 获取 userId
            String userId = getUserId(toolContext);
            if (userId == null) {
                return "Error: User ID not found in context";
            }

            log.info("搜索知识库: userId={}, query={}, fileType={}, topK={}",
                    userId, params.query, params.fileType, params.topK);

            // 执行搜索
            List<Document> results = searchKnowledge(userId, params);

            // 格式化结果
            String formattedResults = knowledgeService.formatAsContext(results);

            if (formattedResults == null || formattedResults.trim().isEmpty()) {
                return "No relevant knowledge found for query: " + params.query;
            }

            log.info("知识库搜索完成: userId={}, 找到 {} 条结果", userId, results.size());
            return formattedResults;

        } catch (Exception e) {
            log.error("知识库搜索失败: query={}", params.query, e);
            return "Error: Failed to search knowledge base: " + e.getMessage();
        }
    }

    private String validateParams(SearchParams params) {
        if (params.query == null || params.query.trim().isEmpty()) {
            return "Query cannot be empty";
        }

        if (params.query.length() > 500) {
            return "Query is too long (max 500 characters)";
        }

        if (params.fileType != null) {
            String fileType = params.fileType.toUpperCase();
            if (!fileType.equals("CODE") && !fileType.equals("DOCUMENT") && !fileType.equals("CONFIG")) {
                return "Invalid file_type. Must be one of: CODE, DOCUMENT, CONFIG";
            }
        }

        if (params.topK != null && (params.topK < 1 || params.topK > 20)) {
            return "top_k must be between 1 and 20";
        }

        return null;
    }

    private String getUserId(ToolContext toolContext) {
        // 调试：打印所有 key，确认 RunnableConfig 的实际注入 key
        log.info("ToolContext keys: {}", toolContext.getContext().keySet());

        // ReactAgent 可能以不同的 key 注入 RunnableConfig，逐一尝试
        String[] configKeys = {"_AGENT_CONFIG_", "config", "runnableConfig", "agentConfig"};
        for (String key : configKeys) {
            try {
                Object obj = toolContext.getContext().get(key);
                if (obj instanceof RunnableConfig runnableConfig) {
                    Object userIdObj = runnableConfig.metadata("userId").orElse(null);
                    if (userIdObj != null) {
                        log.info("从 ToolContext[{}].metadata(userId) 获取到 userId: {}", key, userIdObj);
                        return userIdObj.toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        // 降级：直接从 Sa-Token 当前会话获取
        try {
            if (StpUtil.isLogin()) {
                String userId = StpUtil.getLoginIdAsString();
                log.info("从 Sa-Token 降级获取到 userId: {}", userId);
                return userId;
            }
        } catch (Exception e) {
            log.warn("无法从 Sa-Token 获取 userId", e);
        }

        log.warn("无法获取 userId，ToolContext 内容: {}", toolContext.getContext());
        return null;
    }

    private List<Document> searchKnowledge(String userId, SearchParams params) {
        int topK = params.topK != null ? params.topK : 5;

        if (params.fileType == null) {
            // 通用搜索
            return knowledgeService.search(userId, params.query, topK);
        } else {
            // 按文件类型搜索
            return switch (params.fileType.toUpperCase()) {
                case "CODE" -> knowledgeService.searchCode(userId, params.query, topK);
                case "DOCUMENT" -> knowledgeService.searchDocuments(userId, params.query, topK);
                case "CONFIG" -> knowledgeService.searchConfig(userId, params.query, topK);
                default -> knowledgeService.search(userId, params.query, topK);
            };
        }
    }

    // ==================== BuiltinToolProvider 接口实现 ====================

    @Override
    public String getToolName() {
        return "search_knowledge";
    }

    @Override
    public String getDisplayName() {
        return "搜索知识库";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("search_knowledge", this)
                .description(DESCRIPTION)
                .inputType(SearchParams.class)
                .build();
    }

    /**
     * 搜索参数
     * <p>
     * 注意：参数必须是一个 JSON 对象。
     * 示例: {"query": "用户认证实现", "file_type": "CODE", "top_k": 10}
     * </p>
     */
    public static class SearchParams {

        /**
         * 搜索查询
         */
        @NotBlank(message = "query is required")
        @JsonProperty("query")
        @JsonPropertyDescription("The search query describing what you are looking for semantically. Example: 'user authentication implementation'")
        public String query;

        /**
         * 文件类型过滤 (可选): CODE, DOCUMENT, CONFIG
         */
        @JsonProperty("file_type")
        @JsonPropertyDescription("Filter by file type. Must be one of: CODE, DOCUMENT, CONFIG. Optional.")
        public String fileType;

        /**
         * 返回结果数量 (可选,默认 5)
         */
        @JsonProperty("top_k")
        @JsonPropertyDescription("Number of results to return. Default is 5, max is 20. Optional.")
        public Integer topK;
    }
}

