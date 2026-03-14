import { useState, useEffect } from 'react';

export type StreamingStatus = 'streaming' | 'waiting' | 'complete' | 'idle';

/**
 * 监听文件流式输出状态的 Hook
 * 用于在编辑器中显示"等待后端推送..."等提示
 */
export const useStreamingStatus = (filePath: string): StreamingStatus => {
  const [status, setStatus] = useState<StreamingStatus>('idle');

  useEffect(() => {
    const handleStreamingStatus = (event: CustomEvent<{ filePath: string; status: StreamingStatus }>) => {
      if (event.detail.filePath === filePath) {
        setStatus(event.detail.status);
      }
    };

    window.addEventListener('streamingStatus', handleStreamingStatus as EventListener);

    return () => {
      window.removeEventListener('streamingStatus', handleStreamingStatus as EventListener);
    };
  }, [filePath]);

  return status;
};
