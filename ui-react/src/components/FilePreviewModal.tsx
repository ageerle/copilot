import { useEffect, useState } from "react";
import { useFileStore } from "./WeIde/stores/fileStore";
import { Modal, Button } from "antd";
import { Highlight } from "react-highlight";
import "highlight.js/styles/github.css";

interface FilePreviewModalProps {
  visible: boolean;
  filePath: string;
  onClose: () => void;
}

export const FilePreviewModal: React.FC<FilePreviewModalProps> = ({
  visible,
  filePath,
  onClose,
}) => {
  const { getContent } = useFileStore();
  const [content, setContent] = useState<string>("");

  useEffect(() => {
    if (visible && filePath) {
      const fileContent = getContent(filePath);
      setContent(fileContent || "");
    }
  }, [visible, filePath, getContent]);

  // 获取文件扩展名以确定语言
  const getFileLanguage = (path: string) => {
    const ext = path.split('.').pop()?.toLowerCase() || '';
    const languageMap: Record<string, string> = {
      'js': 'javascript',
      'ts': 'typescript',
      'jsx': 'javascript',
      'tsx': 'typescript',
      'html': 'html',
      'css': 'css',
      'scss': 'scss',
      'sass': 'sass',
      'less': 'less',
      'json': 'json',
      'md': 'markdown',
      'py': 'python',
      'java': 'java',
      'xml': 'xml',
      'yaml': 'yaml',
      'yml': 'yaml',
      'sh': 'bash',
      'sql': 'sql',
      'go': 'go',
      'rs': 'rust',
      'cpp': 'cpp',
      'c': 'c',
      'h': 'c',
      'hpp': 'cpp',
      'cs': 'csharp',
      'php': 'php',
      'rb': 'ruby',
      'swift': 'swift',
      'kt': 'kotlin',
      'scala': 'scala',
      'r': 'r',
      'pl': 'perl',
      'lua': 'lua',
      'toml': 'toml',
      'ini': 'ini',
      'txt': 'plaintext',
    };
    
    return languageMap[ext] || 'plaintext';
  };

  if (!visible) return null;

  return (
    <Modal
      title={`预览: ${filePath}`}
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose}>
          关闭
        </Button>,
      ]}
      width="80%"
      style={{ top: 20 }}
      bodyStyle={{ padding: 0 }}
    >
      <div className="p-4 h-[70vh] overflow-auto">
        <Highlight languages={[getFileLanguage(filePath)]}>
          {content}
        </Highlight>
      </div>
    </Modal>
  );
};