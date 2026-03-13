/**
 * SSE 消息解析器使用示例
 * 保持与 useWebSocketMessageParser.tsx 和 useMessageParser.tsx 类似的使用方式
 * 包含自动重连和错误恢复机制
 */

import React from 'react';
import { SSEMessageParser, OperationCallbackData, FileOperationData, CommandOperationData, SSEEventType } from './sseMessageParser';
import { SSEConnectionManager, SSEConnectionStatus } from './sseConnectionManager';
import { createFileWithContent } from '../WeIde/components/IDEContent/FileExplorer/utils/fileSystem';
import {useFileStore} from '../WeIde/stores/fileStore';
import useTerminalStore from '@/stores/terminalSlice';
import { eventEmitter } from './utils/EventEmitter';
import { Message } from "ai/react";

/**
 * 规范化文件路径
 * 后端已返回相对路径（如 hello.html 或 vue/hello.html）
 * 这里只做兼容处理，确保路径格式正确
 */
function normalizeFilePath(filePath: string): string {
  if (!filePath) return filePath;

  // 1. 统一路径分隔符（Windows 反斜杠转正斜杠）
  let path = filePath.replace(/\\/g, '/');

  // 2. 去掉可能存在的 workspace/ 前缀（兼容旧格式）
  if (path.startsWith('workspace/')) {
    path = path.substring('workspace/'.length);
  }

  return path;
}

// ==================== 伪流式输出管理器 ====================

/**
 * 文件内容缓冲区
 * 用于存储正在流式输出的文件内容
 */
interface FileBuffer {
  filePath: string;
  content: string;
  displayedLength: number;  // 已显示的字符数
  isComplete: boolean;      // 是否接收完成
}

/**
 * 伪流式输出管理器
 * 实现文件内容的逐步显示效果
 */
class StreamingFileManager {
  private buffers: Map<string, FileBuffer> = new Map();
  private displayTimers: Map<string, NodeJS.Timeout> = new Map();
  private refreshTimer: NodeJS.Timeout | null = null;
  private readonly displaySpeed = 10;  // 每次显示的字符数
  private readonly displayInterval = 60;  // 显示间隔(ms)
  private readonly refreshDelay = 1000;  // 刷新文件列表的防抖延迟(ms)

  /**
   * 添加/追加文件内容
   */
  async addContent(filePath: string, content: string) {
    let buffer = this.buffers.get(filePath);

    if (!buffer) {
      // 新文件 - 先创建文件再初始化流式输出
      buffer = {
        filePath,
        content: '',
        displayedLength: 0,
        isComplete: false
      };
      this.buffers.set(filePath, buffer);

      // 先创建文件，等待完成后再选中并打开
      try {
        await createFileWithContent(filePath, '', true);
        selectAndOpenFile(filePath);
        console.log('[StreamingFileManager] 创建并打开文件:', filePath);
      } catch (err) {
        console.error('[StreamingFileManager] 创建文件失败:', err);
        return; // 如果创建失败，不继续处理
      }
    }

    // 追加内容
    buffer.content += content;

    // 启动流式显示
    this.startStreaming(filePath);

    // 重置刷新定时器
    this.scheduleRefresh();
  }

  /**
   * 标记文件接收完成
   */
  completeFile(filePath: string) {
    const buffer = this.buffers.get(filePath);
    if (buffer) {
      buffer.isComplete = true;
    }
  }

  /**
   * 启动流式显示
   */
  private startStreaming(filePath: string) {
    // 清除已有定时器
    const existingTimer = this.displayTimers.get(filePath);
    if (existingTimer) {
      clearInterval(existingTimer);
    }

    // 启动新的显示定时器
    const timer = setInterval(() => {
      this.displayNextChunk(filePath);
    }, this.displayInterval);

    this.displayTimers.set(filePath, timer);
  }

  /**
   * 显示下一块内容
   */
  private displayNextChunk(filePath: string) {
    const buffer = this.buffers.get(filePath);
    if (!buffer) return;

    // 检查是否已显示完毕
    if (buffer.displayedLength >= buffer.content.length) {
      // 内容已全部显示，检查是否完成
      if (buffer.isComplete) {
        const timer = this.displayTimers.get(filePath);
        if (timer) {
          clearInterval(timer);
          this.displayTimers.delete(filePath);
        }
      }
      return;
    }

    // 计算本次显示的内容
    const nextLength = Math.min(
      buffer.displayedLength + this.displaySpeed,
      buffer.content.length
    );

    const displayContent = buffer.content.substring(0, nextLength);
    buffer.displayedLength = nextLength;

    // 更新 fileStore
    useFileStore.getState().updateContent(filePath, displayContent, true, true);
  }

  /**
   * 调度文件列表刷新（防抖）
   */
  private scheduleRefresh() {
    // 清除已有定时器
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    // 设置新定时器
    this.refreshTimer = setTimeout(() => {
      this.refreshFileList();
    }, this.refreshDelay);
  }

  /**
   * 刷新文件列表
   */
  private refreshFileList() {
    // 触发文件列表刷新事件
    window.dispatchEvent(new CustomEvent('refreshFileList'));
    console.log('[StreamingFileManager] 文件列表已刷新');
  }

  /**
   * 清理所有资源
   */
  cleanup() {
    // 清除所有显示定时器
    this.displayTimers.forEach(timer => clearInterval(timer));
    this.displayTimers.clear();

    // 清除刷新定时器
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }

    // 清空缓冲区
    this.buffers.clear();
  }
}

// 全局单例
const streamingFileManager = new StreamingFileManager();

/**
 * 选中文件并在编辑器中打开
 */
function selectAndOpenFile(filePath: string) {
  const { setSelectedPath } = useFileStore.getState();
  setSelectedPath(filePath);

  // 触发 openFile 事件，让编辑器打开文件
  window.dispatchEvent(new CustomEvent('openFile', {
    detail: { path: filePath }
  }));
}

// 命令队列（复用现有实现）
class Queue {
  private queue: string[] = [];
  private processing: boolean = false;

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
          console.log('执行命令', command);
          await useTerminalStore.getState().getTerminal(0).executeCommand(command);
        }
      }
    } finally {
      this.processing = false;
    }
  }
}

export const queue = new Queue();

// 创建解析器实例
const sseMessageParser = new SSEMessageParser({
  // 安全配置
  maxMessageSize: 10 * 1024 * 1024, // 10MB
  maxOperationsPerMessage: 100,
  enableValidation: true,
  timeout: 30000, // 30秒

  // 回调函数 - 使用伪流式输出
  callbacks: {
    // 文件添加操作
    onAddStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      console.log('[SSE] 开始添加文件:', filePath);

      // 创建空文件并初始化流式输出
      await createFileWithContent(filePath, '', true);
      await streamingFileManager.addContent(filePath, '');
    },

    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      const content = fileData.content || '';
      console.log('[SSE] 文件添加进度:', filePath, '内容长度:', content.length);

      // 使用流式管理器追加内容（即使内容为空也会创建文件）
      await streamingFileManager.addContent(filePath, content);
    },

    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      console.log('[SSE] 完成添加文件:', filePath);

      // 标记文件接收完成
      streamingFileManager.completeFile(filePath);
    },

    // 文件编辑操作
    onEditStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      console.log('[SSE] 开始编辑文件:', filePath);

      // 删除现有文件并创建空文件
      await useFileStore.getState().deleteFile(filePath);
      await createFileWithContent(filePath, '', false);

      // 初始化流式输出
      await streamingFileManager.addContent(filePath, '');
    },

    onEditProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      const content = fileData.content || '';
      console.log('[SSE] 文件编辑进度:', filePath, '内容长度:', content.length);

      // 使用流式管理器追加内容
      await streamingFileManager.addContent(filePath, content);
    },

    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      console.log('[SSE] 完成编辑文件:', filePath);

      // 标记文件接收完成
      streamingFileManager.completeFile(filePath);
    },

    // 文件删除操作
    onDeleteStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 开始删除文件:', fileData.filePath);
    },

    onDeleteProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 文件删除进度:', fileData.filePath);
    },

    onDeleteEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      const filePath = normalizeFilePath(fileData.filePath);
      console.log('[SSE] 完成删除文件:', filePath);

      // 删除文件
      try {
        await useFileStore.getState().deleteFile(filePath);
        console.log('[SSE] 已删除文件:', filePath);
      } catch (error) {
        console.error('[SSE] 删除文件失败:', error);
      }
    },

    // 命令执行
    onCmd: async (data: OperationCallbackData) => {
      const cmdData = data.data as CommandOperationData;
      console.log('[SSE] 执行命令:', cmdData.command);

      if (cmdData.command) {
        queue.push(cmdData.command);
      }
    },

    // 错误处理
    onError: (error: Error, message?: any) => {
      console.error('[SSE] 解析器错误:', error);
      console.error('[SSE] 错误消息:', message);
      // 可以在这里实现错误通知逻辑
    },
  },
});

/**
 * 解析 SSE 消息
 * 与 parseWebSocketMessage 和 parseMessages 类似的使用方式
 * 
 * SSE 消息格式：
 * - 后端通过 event 字段发送事件名（如：add-start, add-progress, add-end）
 * - data 字段包含 JSON 格式的操作数据
 * 
 * 示例：
 * event: add-start
 * data: {"type":"add","filePath":"src/index.js","content":"..."}
 */
export const parseSSEMessage = (messageId: string, message: string | object): void => {
  sseMessageParser.parse(messageId, message);
};

/**
 * 重置解析器状态
 */
export const resetSSEParser = (): void => {
  sseMessageParser.reset();
};

/**
 * 清理指定消息的状态
 */
export const clearSSEMessage = (messageId: string): void => {
  sseMessageParser.clearMessage(messageId);
};

/**
 * 获取消息状态
 */
export const getSSEMessageState = (messageId: string) => {
  return sseMessageParser.getMessageState(messageId);
};

// 导出解析器实例（如果需要直接访问）
export { sseMessageParser };

// ==================== SSE 连接管理器 ====================

/**
 * 创建并配置 SSE 连接管理器
 * 
 * SSE 与 WebSocket 的主要区别：
 * 1. SSE 是单向通信（服务器到客户端），基于 HTTP
 * 2. SSE 使用 EventSource API，自动重连
 * 3. SSE 通过 event 字段区分不同的事件类型
 * 4. SSE 只支持 GET 请求，不支持自定义请求头（原生 EventSource）
 */
export const createSSEConnection = (
  url: string,
  onMessage?: (event: MessageEvent) => void
): SSEConnectionManager => {
  const connectionManager = new SSEConnectionManager(
    {
      url,
      withCredentials: false, // 是否发送凭证
    },
    {
      onOpen: (event) => {
        console.log('[SSE] 连接已建立', event);
      },
      onClose: () => {
        console.log('[SSE] 连接已关闭');
      },
      onError: (error) => {
        console.error('[SSE] 连接错误', error);
      },
      onMessage: (event) => {
        try {
          // SSE 消息格式：
          // - event.data 包含 JSON 字符串
          // - event.type 包含事件类型（如果有 event 字段）
          // - 如果没有 event 字段，event.type 为 'message'
          
          let message: any;
          let eventName: string;

          // 解析消息数据
          if (typeof event.data === 'string') {
            try {
              message = JSON.parse(event.data);
            } catch (e) {
              console.error('[SSE] JSON 解析失败:', e);
              return;
            }
          } else {
            message = event.data;
          }

          // 获取事件名
          // 如果后端通过 event 字段发送，event.type 会包含事件名
          // 否则从 message.event 获取
          eventName = event.type !== 'message' ? event.type : (message.event || 'message');

          // 生成消息ID
          const messageId = message.messageId || `msg_${Date.now()}`;

          // 构建完整的消息对象（包含 event 字段）
          const fullMessage = {
            ...message,
            event: eventName,
          };

          // 解析消息
          parseSSEMessage(messageId, fullMessage);
          
          // 调用自定义消息处理器
          onMessage?.(event);
        } catch (error) {
          console.error('[SSE] 解析消息失败:', error);
        }
      },
      onStatusChange: (status: SSEConnectionStatus) => {
        console.log(`[SSE] 连接状态变更: ${status}`);
      },
    }
  );

  // 设置重连配置
  connectionManager.setMaxReconnectAttempts(Infinity); // 无限重连
  connectionManager.setReconnectInterval(1000); // 1秒重连间隔

  // 自动连接
  connectionManager.connect();

  return connectionManager;
};

/**
 * 使用 SSE 连接管理器的示例 Hook
 */
export const useSSEConnection = (
  url: string,
  options?: {
    autoConnect?: boolean;
    onMessage?: (event: MessageEvent) => void;
    onStatusChange?: (status: SSEConnectionStatus) => void;
  }
) => {
  const [connectionManager, setConnectionManager] = React.useState<SSEConnectionManager | null>(null);
  const [status, setStatus] = React.useState<SSEConnectionStatus>('disconnected');

  React.useEffect(() => {
    const manager = new SSEConnectionManager(
      {
        url,
        withCredentials: false,
      },
      {
        onMessage: (event) => {
          try {
            let message: any;
            let eventName: string;

            if (typeof event.data === 'string') {
              try {
                message = JSON.parse(event.data);
              } catch (e) {
                console.error('[SSE] JSON 解析失败:', e);
                return;
              }
            } else {
              message = event.data;
            }

            eventName = event.type !== 'message' ? event.type : (message.event || 'message');
            const messageId = message.messageId || `msg_${Date.now()}`;

            const fullMessage = {
              ...message,
              event: eventName,
            };

            parseSSEMessage(messageId, fullMessage);
            options?.onMessage?.(event);
          } catch (error) {
            console.error('[SSE] 解析消息失败:', error);
          }
        },
        onStatusChange: (newStatus) => {
          setStatus(newStatus);
          options?.onStatusChange?.(newStatus);
        },
      }
    );

    manager.setMaxReconnectAttempts(Infinity);
    manager.setReconnectInterval(1000);

    setConnectionManager(manager);

    if (options?.autoConnect !== false) {
      manager.connect();
    }

    return () => {
      manager.destroy();
    };
  }, [url]);

  return {
    connectionManager,
    status,
    connect: () => connectionManager?.connect(),
    disconnect: () => connectionManager?.close(),
    isConnected: () => connectionManager?.isConnected() ?? false,
  };
};

/**
 * 解析消息数组（兼容原有接口）
 * 如果消息内容包含 JSON 格式的 SSE 消息，则解析
 * 否则保持原有逻辑（用于向后兼容）
 */
export const parseMessages = async (messages: Message[]) => {
  console.log('[SSE] 解析消息数组:', messages);
  for (let i = 0; i < messages.length; i++) {
    const message = messages[i];
    if (message.role === "assistant" && message.content) {
      try {
        console.log('[SSE] 解析消息内容:', message.content);
        // 尝试解析为 JSON 格式的 SSE 消息
        const jsonMessage = JSON.parse(message.content);
        console.log('[SSE] 解析后的JSON消息:', jsonMessage);
        if (jsonMessage.event && jsonMessage.data) {
          // 这是 SSE 格式的消息
          sseMessageParser.parse(message.id, jsonMessage);
        } else {
          // 不是 SSE 格式，可能是其他格式，跳过
          console.warn('[SSE] 消息格式不匹配，跳过:', message.id);
        }
      } catch (e) {
        // 不是 JSON 格式，可能是文本内容，跳过
        // 保持向后兼容，不抛出错误
        console.debug('[SSE] 消息不是 JSON 格式，跳过解析:', message.id);
      }
    }
  }
};

