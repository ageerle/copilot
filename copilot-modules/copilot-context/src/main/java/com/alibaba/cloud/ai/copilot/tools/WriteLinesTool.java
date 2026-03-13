package com.alibaba.cloud.ai.copilot.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 写入文件片段工具
 * 向文件写入内容片段，支持追加或插入到指定行
 */
@Component
public class WriteLinesTool implements BiFunction<WriteLinesTool.WriteLinesParams, ToolContext, String> {

    private final String rootDirectory;
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public static final String DESCRIPTION = """
            Writes content lines to a file incrementally (fragment by fragment).

            IMPORTANT: This tool is designed for INCREMENTAL writing. Do NOT wait until all content is generated.
            Write content in small fragments (e.g., 10-20 lines at a time) as you generate them.
            This allows faster feedback and reduces memory usage.

            Parameters:
            - file_path: Absolute path of the file
            - lines: List of content lines to write (keep fragments small, e.g., 10-20 lines)
            - mode: "append" (default) or "insert"
            - line_number: Line number for insert mode (1-based, default 1)

            Example (incremental append - write first 10 lines):
            {"file_path": "/workspace/project/src/main.java", "lines": ["import java.util.*;", "public class Main {", ...]}

            Example (continue writing next fragment):
            {"file_path": "/workspace/project/src/main.java", "lines": ["    public void run() {", "        // implementation", ...]}
            """;

    public WriteLinesTool() {
        this.rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
    }

    public static ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("write_lines", new WriteLinesTool())
                .description(DESCRIPTION)
                .inputType(WriteLinesParams.class)
                .build();
    }

    @Override
    public String apply(WriteLinesParams params, ToolContext toolContext) {
        try {
            // 验证参数
            String error = validateParams(params);
            if (error != null) {
                return "Error: " + error;
            }

            Path filePath = Paths.get(params.filePath);

            // 验证在工作目录内
            if (!isWithinWorkspace(filePath)) {
                return "Error: file_path must be within workspace (" + rootDirectory + ")";
            }

            // 文件不存在则创建
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
                logger.info("Created file: {}", filePath);
            }

            String mode = params.mode != null ? params.mode.toLowerCase() : "append";

            if ("insert".equals(mode)) {
                return insertLines(filePath, params);
            } else {
                return appendLines(filePath, params);
            }

        } catch (IOException e) {
            logger.error("Error writing to file: {}", params.filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private String validateParams(WriteLinesParams params) {
        if (params.filePath == null || params.filePath.trim().isEmpty()) {
            return "file_path is required";
        }
        if (params.lines == null || params.lines.isEmpty()) {
            return "lines is required and cannot be empty";
        }
        Path path = Paths.get(params.filePath);
        if (!path.isAbsolute()) {
            return "file_path must be absolute: " + params.filePath;
        }
        if (params.lineNumber != null && params.lineNumber < 1) {
            return "line_number must be >= 1";
        }
        return null;
    }

    private String appendLines(Path filePath, WriteLinesParams params) throws IOException {
        // 追加内容，每行前加换行
        StringBuilder sb = new StringBuilder();
        for (String line : params.lines) {
            sb.append(line).append("\n");
        }
        Files.writeString(filePath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        String msg = "Appended " + params.lines.size() + " lines to: " + params.filePath;
        logger.info(msg);
        return msg;
    }

    private String insertLines(Path filePath, WriteLinesParams params) throws IOException {
        List<String> existingLines = Files.readAllLines(filePath);
        int insertAt = params.lineNumber != null ? params.lineNumber - 1 : 0; // 转为0-based

        // 边界检查
        if (insertAt > existingLines.size()) {
            insertAt = existingLines.size();
        }
        if (insertAt < 0) {
            insertAt = 0;
        }

        // 插入新行
        existingLines.addAll(insertAt, params.lines);

        // 写回文件
        Files.write(filePath, existingLines);

        String msg = "Inserted " + params.lines.size() + " lines at line " + (insertAt + 1) + ": " + params.filePath;
        logger.info(msg);
        return msg;
    }

    private boolean isWithinWorkspace(Path path) {
        try {
            Path workspaceRoot = Paths.get(rootDirectory).toRealPath();
            return path.normalize().startsWith(workspaceRoot.normalize());
        } catch (IOException e) {
            return false;
        }
    }

    public static class WriteLinesParams {
        @NotBlank(message = "file_path is required")
        @JsonProperty("file_path")
        @JsonPropertyDescription("The absolute path of the file. Must be within workspace.")
        public String filePath;

        public List<String> lines;

        @JsonProperty("mode")
        @JsonPropertyDescription("Write mode: 'append' (default) or 'insert'.")
        public String mode;

        @JsonProperty("line_number")
        @JsonPropertyDescription("Line number for insert mode (1-based). Default is 1.")
        public Integer lineNumber;

        /**
         * 支持 lines 参数为字符串或数组格式
         * - 数组格式: ["line1", "line2", ...]
         * - 字符串格式: "line1\nline2\n..."
         */
        @JsonSetter("lines")
        public void setLines(Object linesValue) {
            if (linesValue == null) {
                this.lines = new ArrayList<>();
                return;
            }

            if (linesValue instanceof List) {
                // 数组格式
                this.lines = new ArrayList<>();
                for (Object item : (List<?>) linesValue) {
                    this.lines.add(item != null ? item.toString() : "");
                }
            } else if (linesValue instanceof String) {
                // 字符串格式，按换行符分割
                String content = (String) linesValue;
                if (content.isEmpty()) {
                    this.lines = new ArrayList<>();
                } else {
                    this.lines = Arrays.asList(content.split("\n", -1));
                }
            } else {
                // 其他格式，转为字符串后按换行分割
                String content = linesValue.toString();
                this.lines = Arrays.asList(content.split("\n", -1));
            }
        }
    }
}
