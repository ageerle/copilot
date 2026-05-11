package com.alibaba.cloud.ai.copilot.service.harness;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HarnessBuiltinToolCatalog {

    public record BuiltinToolMeta(String name, String displayName, String description) {}

    public List<BuiltinToolMeta> all() {
        return List.of(
                new BuiltinToolMeta("save_memory", "保存记忆", "保存记忆到长期存储。参数: namespace(数组), key(字符串), value(JSON对象)"),
                new BuiltinToolMeta("get_memory", "获取记忆", "从长期存储中获取记忆。参数: namespace(数组), key(字符串)"),
                new BuiltinToolMeta("search_memory", "搜索记忆", "在命名空间内搜索相关记忆。参数: namespace(数组), filter(JSON对象可选)"),
                new BuiltinToolMeta("learn_user_preference", "学习用户偏好", "学习并保存用户偏好。参数: category, value, context(可选), confidence(可选0.0-1.0)"),
                new BuiltinToolMeta("search_knowledge", "搜索知识库", "搜索用户知识库。参数: user_id, query, file_type(CODE|DOCUMENT|CONFIG可选), top_k可选"),
                new BuiltinToolMeta("read_file", "读取文件", "读取文件内容。参数: file_path(绝对路径), offset(可选,从1开始), limit(可选,默认200)"),
                new BuiltinToolMeta("edit_file", "编辑文件", "在文件中进行精确替换。参数: file_path, old_string, new_string"),
                new BuiltinToolMeta("create_file", "创建文件", "创建空文件。参数: file_path(绝对路径)"),
                new BuiltinToolMeta("write_lines", "写入文件", "向文件追加或插入内容。参数: file_path, lines, mode(可选), line_number(可选)"),
                new BuiltinToolMeta("list_directory", "列出目录", "列出目录内容。参数: file_path, recursive(可选), max_depth(可选)"));
    }

    public List<String> supportedToolNames() {
        return all().stream().map(BuiltinToolMeta::name).toList();
    }
}
