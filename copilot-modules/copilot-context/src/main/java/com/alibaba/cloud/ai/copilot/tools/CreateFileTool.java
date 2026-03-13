package com.alibaba.cloud.ai.copilot.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;

/**
 * 创建文件工具
 * 创建指定的空文件，如果父目录不存在会自动创建
 */
@Component
public class CreateFileTool implements BiFunction<CreateFileTool.CreateFileParams, ToolContext, String> {

    private final String rootDirectory;
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public static final String DESCRIPTION = """
            Creates an empty file at the specified path.
            Parent directories will be created if they don't exist.

            Example: {"file_path": "/workspace/project/src/main.java"}
            """;

    public CreateFileTool() {
        this.rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
    }

    public static ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("create_file", new CreateFileTool())
                .description(DESCRIPTION)
                .inputType(CreateFileParams.class)
                .build();
    }

    @Override
    public String apply(CreateFileParams params, ToolContext toolContext) {
        try {
            // 验证参数
            if (params.filePath == null || params.filePath.trim().isEmpty()) {
                return "Error: file_path is required";
            }

            Path filePath = Paths.get(params.filePath);

            // 验证绝对路径
            if (!filePath.isAbsolute()) {
                return "Error: file_path must be absolute: " + params.filePath;
            }

            // 验证在工作目录内
            if (!isWithinWorkspace(filePath)) {
                return "Error: file_path must be within workspace (" + rootDirectory + ")";
            }

            // 文件已存在
            if (Files.exists(filePath)) {
                return "Error: File already exists: " + params.filePath;
            }

            // 创建父目录
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                logger.info("Created parent directories: {}", parent);
            }

            // 创建空文件
            Files.createFile(filePath);
            logger.info("Created file: {}", filePath);

            return "Created: " + params.filePath;

        } catch (IOException e) {
            logger.error("Error creating file: {}", params.filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private boolean isWithinWorkspace(Path path) {
        try {
            Path workspaceRoot = Paths.get(rootDirectory).toRealPath();
            return path.normalize().startsWith(workspaceRoot.normalize());
        } catch (IOException e) {
            return false;
        }
    }

    public static class CreateFileParams {
        @NotBlank(message = "file_path is required")
        @JsonProperty("file_path")
        @JsonPropertyDescription("The absolute path of the file to create. Must be within workspace.")
        public String filePath;
    }
}