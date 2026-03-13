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
 * 删除文件工具
 * 删除指定的文件
 */
@Component
public class DeleteFileTool implements BiFunction<DeleteFileTool.DeleteFileParams, ToolContext, String> {

    private final String rootDirectory;
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public static final String DESCRIPTION = """
            Deletes the specified file.
            Only works on files, not directories.

            Example: {"file_path": "/workspace/project/src/main.java"}
            """;

    public DeleteFileTool() {
        this.rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
    }

    public static ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("delete_file", new DeleteFileTool())
                .description(DESCRIPTION)
                .inputType(DeleteFileParams.class)
                .build();
    }

    @Override
    public String apply(DeleteFileParams params, ToolContext toolContext) {
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

            // 文件不存在
            if (!Files.exists(filePath)) {
                return "Error: File not found: " + params.filePath;
            }

            // 不能删除目录
            if (Files.isDirectory(filePath)) {
                return "Error: Cannot delete directory, only files: " + params.filePath;
            }

            // 删除文件
            Files.delete(filePath);
            logger.info("Deleted file: {}", filePath);

            return "Deleted: " + params.filePath;

        } catch (IOException e) {
            logger.error("Error deleting file: {}", params.filePath, e);
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

    public static class DeleteFileParams {
        @NotBlank(message = "file_path is required")
        @JsonProperty("file_path")
        @JsonPropertyDescription("The absolute path of the file to delete. Must be within workspace.")
        public String filePath;
    }
}