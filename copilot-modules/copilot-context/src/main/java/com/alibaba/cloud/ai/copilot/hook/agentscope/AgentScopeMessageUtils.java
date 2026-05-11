package com.alibaba.cloud.ai.copilot.hook.agentscope;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentScopeMessageUtils {

    static Msg textMessage(MsgRole role, String text) {
        return Msg.builder()
                .role(role)
                .textContent(text != null ? text : "")
                .build();
    }

    static String textContent(Msg msg) {
        return msg != null ? msg.getTextContent() : "";
    }

    public static String plainTextTranscript(List<Msg> messages) {
        StringBuilder transcript = new StringBuilder();
        for (Msg message : messages) {
            if (message.getRole() == MsgRole.SYSTEM) {
                transcript.append("[System Context]\n");
            } else if (message.getRole() == MsgRole.ASSISTANT) {
                transcript.append("[Assistant]\n");
            } else if (message.getRole() == MsgRole.TOOL) {
                transcript.append("[Tool]\n");
            } else {
                transcript.append("[User]\n");
            }
            transcript.append(textContent(message)).append("\n\n");
        }
        return transcript.toString().trim();
    }

    static boolean hasToolUse(Msg msg) {
        return msg != null && !msg.getContentBlocks(ToolUseBlock.class).isEmpty();
    }

    static boolean hasToolResult(Msg msg) {
        return msg != null && !msg.getContentBlocks(ToolResultBlock.class).isEmpty();
    }

    static String roleOf(Msg msg) {
        if (msg == null || msg.getRole() == null) {
            return "unknown";
        }
        return msg.getRole().name().toLowerCase();
    }

    static Msg appendText(Msg msg, String extraText) {
        List<ContentBlock> content = new ArrayList<>(msg.getContent());
        content.add(TextBlock.builder().text(extraText).build());
        return Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(content)
                .metadata(msg.getMetadata())
                .timestamp(msg.getTimestamp())
                .build();
    }
}
