import {FileTree} from './FileTree';
import {buildFileTree} from '../utils/fileTree';

interface FileListProps {
  files: Record<string, string>;  // files 对象，key 是文件路径，value 是文件内容
  onFileSelect: (path: string) => void;
}

export function FileList({ files, onFileSelect }: FileListProps) {
  // 从 files 对象获取文件路径列表
  const filePaths = Object.keys(files);
  const fileTree = buildFileTree(filePaths);

  return (
    <div className="px-2 py-1">
      <FileTree items={fileTree} onFileSelect={onFileSelect} />
    </div>
  );
}