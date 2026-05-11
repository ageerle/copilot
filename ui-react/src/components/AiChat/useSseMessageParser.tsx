import {Message} from "ai/react";
import {
  SSEMessageParser,
  OperationCallbackData,
  FileOperationData,
  CommandOperationData,
} from "./sseMessageParser";
import {readWorkspaceFile} from "@/api/filesystem";
import {createFileWithContent} from "../WeIde/components/IDEContent/FileExplorer/utils/fileSystem";
import {useFileStore} from "../WeIde/stores/fileStore";
import useTerminalStore from "@/stores/terminalSlice";

function normalizeFilePath(filePath: string): string {
  if (!filePath) return filePath;
  let path = filePath.replace(/\\/g, "/");
  if (path.startsWith("workspace/")) {
    path = path.substring("workspace/".length);
  }
  return path;
}

interface FileBufferState {
  filePath: string;
  contentPool: string;
  isComplete: boolean;
}

class FileBufferManager {
  private states = new Map<string, FileBufferState>();
  private refreshTimer: NodeJS.Timeout | null = null;
  private readonly refreshDelay = 1000;

  async start(filePath: string) {
    if (this.states.has(filePath)) return;

    this.states.set(filePath, {
      filePath,
      contentPool: "",
      isComplete: false,
    });

    try {
      await createFileWithContent(filePath, "", true);
    } catch (error) {
      console.error("[FileBufferManager] create file failed:", error);
    }
  }

  async append(filePath: string, content: string) {
    await this.start(filePath);
    const state = this.states.get(filePath);
    if (!state) return;

    state.contentPool += content;
    this.scheduleRefresh();
  }

  async finalize(filePath: string) {
    const state = this.states.get(filePath);
    if (!state) return;

    state.isComplete = true;

    const fallbackContent = state.contentPool;
    const projectRoot = useFileStore.getState().projectRoot || "workspace";

    try {
      const response = await readWorkspaceFile(projectRoot, filePath);
      const content =
        response.success && response.content !== undefined
          ? response.content
          : fallbackContent;

      await useFileStore.getState().updateContent(filePath, content, true, true);
    } catch (error) {
      console.error("[FileBufferManager] finalize failed:", error);
      await useFileStore.getState().updateContent(
        filePath,
        fallbackContent,
        true,
        true
      );
    } finally {
      this.states.delete(filePath);
    }
  }

  clear(filePath: string) {
    this.states.delete(filePath);
  }

  cleanup() {
    this.states.clear();
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  getState(filePath: string): FileBufferState | undefined {
    return this.states.get(filePath);
  }

  private scheduleRefresh() {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    this.refreshTimer = setTimeout(() => {
      window.dispatchEvent(new CustomEvent("refreshFileList"));
    }, this.refreshDelay);
  }
}

const fileBufferManager = new FileBufferManager();

class Queue {
  private queue: string[] = [];
  private processing = false;

  push(command: string) {
    this.queue.push(command);
    this.process();
  }

  private getNext(): string | undefined {
    return this.queue.shift();
  }

  private async process() {
    if (this.processing || this.queue.length === 0) {
      return;
    }

    this.processing = true;
    try {
      while (this.queue.length > 0) {
        const command = this.getNext();
        if (command) {
          await useTerminalStore.getState().getTerminal(0).executeCommand(command);
        }
      }
    } finally {
      this.processing = false;
    }
  }
}

export const queue = new Queue();

const sseMessageParser = new SSEMessageParser({
  maxMessageSize: 10 * 1024 * 1024,
  maxOperationsPerMessage: 100,
  enableValidation: true,
  timeout: 30000,
  callbacks: {
    onAddStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await fileBufferManager.start(filePath);
    },
    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await fileBufferManager.append(filePath, fileData.content || "");
    },
    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await fileBufferManager.finalize(filePath);
    },
    onEditStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await useFileStore.getState().deleteFile(filePath);
      await fileBufferManager.start(filePath);
    },
    onEditProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await fileBufferManager.append(filePath, fileData.content || "");
    },
    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await fileBufferManager.finalize(filePath);
    },
    onDeleteEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      await useFileStore.getState().deleteFile(filePath);
      fileBufferManager.clear(filePath);
    },
    onCmd: async (data: OperationCallbackData) => {
      const cmdData = data.data as CommandOperationData;
      if (cmdData.command) {
        queue.push(cmdData.command);
      }
    },
    onError: (error: Error, message?: any) => {
      console.error("[SSEMessageParser] parse error:", error, message);
    },
  },
});

export const parseSSEMessage = (messageId: string, message: string | object): void => {
  sseMessageParser.parse(messageId, message);
};

export const resetSSEParser = (): void => {
  sseMessageParser.reset();
  fileBufferManager.cleanup();
};

export const clearSSEMessage = (messageId: string): void => {
  sseMessageParser.clearMessage(messageId);
};

export const getSSEMessageState = (messageId: string) => {
  return sseMessageParser.getMessageState(messageId);
};

export const parseMessages = async (messages: Message[]) => {
  for (const message of messages) {
    if (message.role !== "assistant" || !message.content) {
      continue;
    }

    try {
      const parsed = JSON.parse(message.content);
      if (parsed && parsed.event && parsed.data) {
        sseMessageParser.parse(message.id, parsed);
      }
    } catch {
      // Ignore plain text messages.
    }
  }
};

export {sseMessageParser};
