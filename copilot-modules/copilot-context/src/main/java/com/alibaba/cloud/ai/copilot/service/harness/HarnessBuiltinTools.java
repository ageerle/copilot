package com.alibaba.cloud.ai.copilot.service.harness;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeService;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessBuiltinTools {

    private final DatabaseStore databaseStore;
    private final KnowledgeService knowledgeService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(
            name = "save_memory",
            description = "保存记忆到长期存储。参数: namespace(数组), key(字符串), value(JSON对象)")
    public String saveMemory(
            @ToolParam(name = "namespace", description = "命名空间数组") List<String> namespace,
            @ToolParam(name = "key", description = "记忆键") String key,
            @ToolParam(name = "value", description = "记忆值对象") Map<String, Object> value) {
        if (namespace == null || namespace.isEmpty()) {
            return "Error: namespace 不能为空";
        }
        if (key == null || key.isBlank()) {
            return "Error: key 不能为空";
        }
        if (value == null) {
            return "Error: value 不能为空";
        }
        databaseStore.putItem(StoreItem.of(namespace, key, value));
        return "成功保存记忆: namespace=" + namespace + ", key=" + key;
    }

    @Tool(name = "get_memory", description = "从长期存储中获取记忆。参数: namespace(数组), key(字符串)")
    public String getMemory(
            @ToolParam(name = "namespace", description = "命名空间数组") List<String> namespace,
            @ToolParam(name = "key", description = "记忆键") String key) {
        if (namespace == null || namespace.isEmpty()) {
            return "Error: namespace 不能为空";
        }
        if (key == null || key.isBlank()) {
            return "Error: key 不能为空";
        }
        return databaseStore
                .getItem(namespace, key)
                .map(
                        item -> {
                            try {
                                return "找到记忆: namespace="
                                        + namespace
                                        + ", key="
                                        + key
                                        + "\n值: "
                                        + objectMapper.writeValueAsString(item.getValue());
                            } catch (Exception e) {
                                return "找到记忆: namespace=" + namespace + ", key=" + key;
                            }
                        })
                .orElse("未找到记忆: namespace=" + namespace + ", key=" + key);
    }

    @Tool(
            name = "search_memory",
            description = "在命名空间内搜索相关记忆。参数: namespace(数组), filter(JSON对象可选)")
    public String searchMemory(
            @ToolParam(name = "namespace", description = "命名空间数组") List<String> namespace,
            @ToolParam(name = "filter", description = "过滤条件JSON对象", required = false)
                    Map<String, Object> filter) {
        if (namespace == null || namespace.isEmpty()) {
            return "Error: namespace 不能为空";
        }
        List<StoreItem> items = databaseStore.searchItems(namespace, filter);
        if (items.isEmpty()) {
            return "未找到记忆: namespace=" + namespace;
        }
        StringBuilder result = new StringBuilder();
        result.append("找到 ").append(items.size()).append(" 条记忆:\n");
        for (StoreItem item : items) {
            try {
                result.append("- key: ")
                        .append(item.getKey())
                        .append(", value: ")
                        .append(objectMapper.writeValueAsString(item.getValue()))
                        .append("\n");
            } catch (Exception e) {
                result.append("- key: ").append(item.getKey()).append("\n");
            }
        }
        return result.toString();
    }

    @Tool(
            name = "learn_user_preference",
            description =
                    "学习并保存用户偏好。参数: category, value, context(可选), confidence(可选0.0-1.0)")
    public String learnUserPreference(
            @ToolParam(name = "category", description = "偏好类别") String category,
            @ToolParam(name = "value", description = "偏好值") String value,
            @ToolParam(name = "context", description = "原始上下文", required = false) String context,
            @ToolParam(name = "confidence", description = "置信度", required = false)
                    Double confidence) {
        if (category == null || category.isBlank()) {
            return "Error: category 不能为空";
        }
        if (value == null || value.isBlank()) {
            return "Error: value 不能为空";
        }
        double score = confidence == null ? 0.8 : Math.max(0.0, Math.min(1.0, confidence));
        Long userId = null;
        try {
            userId = LoginHelper.getUserId();
        } catch (Exception ignored) {
        }
        String userKey = "user_" + (userId == null ? "anonymous" : userId);
        List<String> namespace = List.of("user_preferences");

        List<Map<String, Object>> items = new ArrayList<>();
        Optional<StoreItem> existing = databaseStore.getItem(namespace, userKey);
        if (existing.isPresent()) {
            Object oldItems = existing.get().getValue().get("items");
            if (oldItems instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pref = (Map<String, Object>) map;
                        items.add(pref);
                    }
                }
            }
        }

        boolean updated = false;
        for (Map<String, Object> pref : items) {
            if (category.equals(String.valueOf(pref.get("category")))
                    && value.equals(String.valueOf(pref.get("value")))) {
                pref.put("confidence", score);
                pref.put("context", context);
                pref.put("updatedAt", LocalDateTime.now().toString());
                pref.put("usageCount", ((Number) pref.getOrDefault("usageCount", 0)).intValue() + 1);
                updated = true;
                break;
            }
        }

        if (!updated) {
            Map<String, Object> pref =
                    Map.of(
                            "category", category,
                            "value", value,
                            "context", context == null ? "" : context,
                            "confidence", score,
                            "learnedAt", LocalDateTime.now().toString(),
                            "usageCount", 1,
                            "enabled", true,
                            "source", "harness");
            items.add(pref);
        }

        databaseStore.putItem(StoreItem.of(namespace, userKey, Map.of("items", items)));
        return (updated ? "成功更新偏好: " : "成功保存偏好: ") + category + "=" + value;
    }

    @Tool(
            name = "search_knowledge",
            description = "搜索用户知识库。参数: user_id, query, file_type(CODE|DOCUMENT|CONFIG可选), top_k可选")
    public String searchKnowledge(
            @ToolParam(name = "user_id", description = "用户ID字符串") String userId,
            @ToolParam(name = "query", description = "语义检索查询") String query,
            @ToolParam(name = "file_type", description = "可选文件类型 CODE|DOCUMENT|CONFIG", required = false)
                    String fileType,
            @ToolParam(name = "top_k", description = "返回结果数量，默认5", required = false) Integer topK) {
        if (userId == null || userId.isBlank()) {
            return "Error: user_id 不能为空";
        }
        if (query == null || query.isBlank()) {
            return "Error: query 不能为空";
        }
        int k = (topK == null || topK < 1) ? 5 : Math.min(topK, 20);

        List<Document> results;
        if (fileType == null || fileType.isBlank()) {
            results = knowledgeService.search(userId, query, k);
        } else {
            results =
                    switch (fileType.toUpperCase()) {
                        case "CODE" -> knowledgeService.searchCode(userId, query, k);
                        case "DOCUMENT" -> knowledgeService.searchDocuments(userId, query, k);
                        case "CONFIG" -> knowledgeService.searchConfig(userId, query, k);
                        default -> knowledgeService.search(userId, query, k);
                    };
        }
        String formatted = knowledgeService.formatAsContext(results);
        if (formatted == null || formatted.isBlank()) {
            return "No relevant knowledge found for query: " + query;
        }
        return formatted;
    }

    @Tool(
            name = "read_file",
            description = "读取文件内容。参数: file_path(绝对路径), offset(可选,从1开始), limit(可选,默认200)")
    public String readFile(
            @ToolParam(name = "file_path", description = "文件绝对路径") String filePath,
            @ToolParam(name = "offset", description = "起始行号", required = false) Integer offset,
            @ToolParam(name = "limit", description = "读取行数", required = false) Integer limit) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file_path 不能为空";
        }
        Path path = Path.of(filePath);
        String scopeError = validateWorkspacePath(path);
        if (scopeError != null) {
            return scopeError;
        }
        if (!Files.exists(path)) {
            return "Error: 文件不存在: " + filePath;
        }
        if (!Files.isRegularFile(path)) {
            return "Error: 路径不是文件: " + filePath;
        }
        int from = (offset == null || offset < 1) ? 1 : offset;
        int size = (limit == null || limit < 1) ? 200 : Math.min(limit, 2000);
        try {
            List<String> lines = Files.readAllLines(path);
            int start = Math.min(from - 1, lines.size());
            int end = Math.min(start + size, lines.size());
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            if (sb.isEmpty()) {
                return "";
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "edit_file",
            description = "在文件中进行精确替换。参数: file_path, old_string, new_string")
    public String editFile(
            @ToolParam(name = "file_path", description = "文件绝对路径") String filePath,
            @ToolParam(name = "old_string", description = "待替换原始字符串") String oldString,
            @ToolParam(name = "new_string", description = "替换后的字符串") String newString) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file_path 不能为空";
        }
        if (oldString == null || oldString.isEmpty()) {
            return "Error: old_string 不能为空";
        }
        if (newString == null) {
            return "Error: new_string 不能为空";
        }
        Path path = Path.of(filePath);
        String scopeError = validateWorkspacePath(path);
        if (scopeError != null) {
            return scopeError;
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return "Error: 文件不存在或不可编辑: " + filePath;
        }
        try {
            String content = Files.readString(path);
            int first = content.indexOf(oldString);
            if (first < 0) {
                return "Error: old_string 未找到";
            }
            int second = content.indexOf(oldString, first + oldString.length());
            if (second >= 0) {
                return "Error: old_string 出现多次，请提供更精确内容";
            }
            String updated = content.replace(oldString, newString);
            Files.writeString(path, updated, StandardOpenOption.TRUNCATE_EXISTING);
            return "编辑成功: " + filePath;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "create_file", description = "创建空文件。参数: file_path(绝对路径)")
    public String createFile(
            @ToolParam(name = "file_path", description = "文件绝对路径") String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file_path 不能为空";
        }
        Path path = Path.of(filePath);
        String scopeError = validateWorkspacePath(path);
        if (scopeError != null) {
            return scopeError;
        }
        try {
            if (Files.exists(path)) {
                return "Error: 文件已存在: " + filePath;
            }
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.createFile(path);
            return "Created: " + filePath;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "write_lines",
            description = "向文件追加或插入内容。参数: file_path, lines, mode(可选append|insert), line_number(可选)")
    public String writeLines(
            @ToolParam(name = "file_path", description = "文件绝对路径") String filePath,
            @ToolParam(name = "lines", description = "待写入行列表") List<String> lines,
            @ToolParam(name = "mode", description = "append或insert", required = false) String mode,
            @ToolParam(name = "line_number", description = "插入行号，从1开始", required = false)
                    Integer lineNumber) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file_path 不能为空";
        }
        if (lines == null || lines.isEmpty()) {
            return "Error: lines 不能为空";
        }
        Path path = Path.of(filePath);
        String scopeError = validateWorkspacePath(path);
        if (scopeError != null) {
            return scopeError;
        }
        try {
            if (!Files.exists(path)) {
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                Files.createFile(path);
            }
            String writeMode = mode == null ? "append" : mode.toLowerCase();
            if ("insert".equals(writeMode)) {
                List<String> existing = Files.readAllLines(path);
                int insertAt = lineNumber == null ? 0 : Math.max(0, Math.min(lineNumber - 1, existing.size()));
                existing.addAll(insertAt, lines);
                Files.write(path, existing);
                return "Inserted " + lines.size() + " lines at line " + (insertAt + 1) + ": " + filePath;
            }
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append("\n");
            }
            Files.writeString(path, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Appended " + lines.size() + " lines to: " + filePath;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "list_directory", description = "列出目录内容。参数: file_path, recursive(可选), max_depth(可选)")
    public String listDirectory(
            @ToolParam(name = "file_path", description = "目录绝对路径") String filePath,
            @ToolParam(name = "recursive", description = "是否递归", required = false) Boolean recursive,
            @ToolParam(name = "max_depth", description = "最大深度", required = false) Integer maxDepth) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file_path 不能为空";
        }
        Path path = Path.of(filePath);
        String scopeError = validateWorkspacePath(path);
        if (scopeError != null) {
            return scopeError;
        }
        if (!Files.exists(path)) {
            return "Error: 目录不存在: " + filePath;
        }
        if (!Files.isDirectory(path)) {
            return "Error: 路径不是目录: " + filePath;
        }
        int depth = maxDepth == null ? 3 : Math.max(1, Math.min(maxDepth, 10));
        boolean deep = Boolean.TRUE.equals(recursive);
        try {
            List<String> lines = new ArrayList<>();
            if (deep) {
                try (var stream = Files.walk(path, depth)) {
                    stream.filter(p -> !p.equals(path))
                            .forEach(
                                    p -> lines.add(
                                            (Files.isDirectory(p) ? "DIR  " : "FILE ")
                                                    + path.relativize(p)));
                }
            } else {
                try (var stream = Files.list(path)) {
                    stream.forEach(
                            p -> lines.add(
                                    (Files.isDirectory(p) ? "DIR  " : "FILE ")
                                            + path.relativize(p)));
                }
            }
            if (lines.isEmpty()) {
                return "Directory is empty.";
            }
            return String.join("\n", lines);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    private String validateWorkspacePath(Path path) {
        if (!path.isAbsolute()) {
            return "Error: file_path 必须是绝对路径";
        }
        try {
            Path root = Path.of(appProperties.getWorkspace().getRootDirectory()).toRealPath();
            Path normalized = path.normalize();
            if (!normalized.startsWith(root)) {
                return "Error: file_path 必须位于工作目录内: " + root;
            }
            return null;
        } catch (IOException e) {
            return "Error: 工作目录校验失败: " + e.getMessage();
        }
    }
}
