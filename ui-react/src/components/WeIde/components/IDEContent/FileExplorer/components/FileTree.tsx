import {useCallback, useEffect, useState} from "react";
import {FileTreeItem} from "./FileTreeItem";
import {CreateFileDialog} from "./CreateFileDialog";
import {CreateFolderDialog} from "./CreateFolderDialog";
import {createFile, createFolder} from "../utils/fileSystem";
import {FileTreeProps} from "../types";
import {cn} from "@/utils/cn";
import {useFileStore} from "@/components/WeIde/stores/fileStore";

export function FileTree({items, onFileSelect}: FileTreeProps) {
  const defaultExpanded = {
    src: true,
    features: true,
    components: true,
  };

  const [expandedFolders, setExpandedFolders] =
    useState<Record<string, boolean>>(defaultExpanded);
  const [showCreateFile, setShowCreateFile] = useState(false);
  const [showCreateFolder, setShowCreateFolder] = useState(false);
  const [selectedPath, setSelectedPath] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  const {selectedPath: globalSelectedPath} = useFileStore();

  useEffect(() => {
    if (globalSelectedPath && globalSelectedPath !== selectedFile) {
      setSelectedFile(globalSelectedPath);
    }
  }, [globalSelectedPath, selectedFile]);

  const toggleFolder = useCallback((path: string, isExpend?: boolean) => {
    setExpandedFolders((prev) => ({
      ...prev,
      [path]: typeof isExpend === "boolean" ? isExpend : !prev[path],
    }));
  }, []);

  const handleCreateFile = async (fileName: string) => {
    const newPath = await createFile(selectedPath, fileName);
    setShowCreateFile(false);
    onFileSelect(newPath);
  };

  const handleCreateFolder = (folderName: string) => {
    createFolder(selectedPath, folderName);
    setShowCreateFolder(false);
    setExpandedFolders((prev) => ({
      ...prev,
      [selectedPath]: true,
    }));
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  return (
    <>
      <div
        className={cn(
          "flex h-full flex-col overflow-auto px-1 py-2",
          "scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-transparent",
          isDragging && "bg-gray-800/20"
        )}
        role="tree"
        aria-label="File explorer"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {items.map((item) => (
          <FileTreeItem
            key={item.path}
            item={item}
            expandedFolders={expandedFolders}
            expanded={!!expandedFolders[item.path]}
            onToggle={toggleFolder}
            onFileSelect={(path) => {
              setSelectedFile(path);
              onFileSelect(path);
            }}
            selectedFile={selectedFile}
          />
        ))}
      </div>

      {showCreateFile && (
        <CreateFileDialog
          path={selectedPath}
          onSubmit={handleCreateFile}
          onCancel={() => setShowCreateFile(false)}
        />
      )}
      {showCreateFolder && (
        <CreateFolderDialog
          path={selectedPath}
          onSubmit={handleCreateFolder}
          onCancel={() => setShowCreateFolder(false)}
        />
      )}
    </>
  );
}

