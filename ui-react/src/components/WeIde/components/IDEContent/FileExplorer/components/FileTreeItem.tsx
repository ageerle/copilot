import {useState} from "react";
import {ChevronDown, ChevronRight} from "lucide-react";
import {FileContextMenu} from "./FileContextMenu";
import {FolderContextMenu} from "./FolderContextMenu";
import {InlineEdit} from "./InlineEdit";
import {CreateDialog} from "./CreateDialog";
import {FileItem} from "../types";
import {createFile, createFolder, deleteFile, renameFile} from "../utils/fileSystem";
import FileIcon from "./fileIcon";
import {cn} from "@/utils/cn";

interface FileTreeItemProps {
  item: FileItem;
  expanded: boolean;
  onToggle: (path: string, isExpend?: boolean) => void;
  onFileSelect: (path: string) => void;
  expandedFolders: Record<string, boolean>;
  selectedFile?: string;
}

export function FileTreeItem({
  item,
  expanded,
  onToggle,
  onFileSelect,
  expandedFolders,
  selectedFile,
}: FileTreeItemProps) {
  const [contextMenu, setContextMenu] = useState<{x: number; y: number} | null>(null);
  const [isRenaming, setIsRenaming] = useState(false);
  const [createDialog, setCreateDialog] = useState<"file" | "folder" | null>(null);

  const isSelected = selectedFile === item.path;

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (item.type === "folder") {
      onToggle(item.path);
      return;
    }

    onFileSelect(item.path);
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    setContextMenu({x: e.clientX - 410, y: e.clientY - 100});
    e.preventDefault();
    e.stopPropagation();
  };

  const handleCreateFile = async (name: string) => {
    const newPath = await createFile(item.path, name);
    onFileSelect(newPath);
  };

  const handleCreateFolder = (name: string) => {
    createFolder(item.path, name);
    onToggle(item.path, true);
  };

  const handleRename = (newName: string) => {
    if (newName && newName !== item.name) {
      renameFile(item.path, newName);
    }
    setIsRenaming(false);
    setContextMenu(null);
  };

  const handleDelete = () => {
    if (confirm(`Are you sure you want to delete ${item.name}?`)) {
      deleteFile(item.path);
    }
    setContextMenu(null);
  };

  return (
    <div className="flex flex-col">
      <div
        className={cn(
          "group relative my-[1px] flex h-[24px] cursor-pointer select-none items-center rounded-sm px-1.5 text-[13px] transition-all duration-200",
          isSelected
            ? "bg-[#e4e6f1] text-[#333] dark:bg-[#37373d] dark:text-white"
            : "text-[#444444] hover:bg-[#f0f0f0] dark:text-gray-300 dark:hover:bg-[#2d2d2d]/70"
        )}
        onClick={handleClick}
        onContextMenu={handleContextMenu}
        role="treeitem"
        tabIndex={0}
        aria-selected={isSelected}
        aria-expanded={item.type === "folder" ? expanded : undefined}
      >
        <span className="inline-flex min-w-0 flex-1 items-center gap-0.5">
          {item.type === "folder" && (
            <span className="flex h-4 w-4 items-center justify-center">
              {expanded ? (
                <ChevronDown className="h-3.5 w-3.5 text-[#424242] transition-transform duration-200 dark:text-gray-400" />
              ) : (
                <ChevronRight className="h-3.5 w-3.5 text-[#424242] transition-transform duration-200 dark:text-gray-400" />
              )}
            </span>
          )}
          <span className="flex h-4.5 w-4.5 items-center justify-center">
            <FileIcon fileName={item.name} />
          </span>
          {isRenaming ? (
            <InlineEdit
              value={item.name}
              onSubmit={handleRename}
              onCancel={() => setIsRenaming(false)}
            />
          ) : (
            <span className="truncate py-0.5">{item.name}</span>
          )}
        </span>
      </div>

      {contextMenu && (
        <div className="fixed inset-0 z-50" onClick={() => setContextMenu(null)}>
          {item.type === "file" ? (
            <FileContextMenu
              x={contextMenu.x}
              y={contextMenu.y}
              path={item.path}
              onClose={() => setContextMenu(null)}
              onRename={() => setIsRenaming(true)}
              onDelete={handleDelete}
            />
          ) : (
            <FolderContextMenu
              x={contextMenu.x}
              y={contextMenu.y}
              path={item.path}
              onClose={() => setContextMenu(null)}
              onRename={() => setIsRenaming(true)}
              onDelete={handleDelete}
              onCreateFile={() => setCreateDialog("file")}
              onCreateFolder={() => setCreateDialog("folder")}
            />
          )}
        </div>
      )}

      <CreateDialog
        type={createDialog || "file"}
        isOpen={createDialog !== null}
        path={item.path}
        onSubmit={createDialog === "file" ? handleCreateFile : handleCreateFolder}
        onClose={() => setCreateDialog(null)}
      />

      {expanded && item.type === "folder" && item.children && (
        <div className={cn("flex flex-col overflow-hidden pl-4", "transition-all duration-200")}>
          {item.children.map((child) => (
            <FileTreeItem
              key={child.path}
              expandedFolders={expandedFolders}
              item={child}
              expanded={!!expandedFolders[child.path]}
              onToggle={onToggle}
              onFileSelect={onFileSelect}
              selectedFile={selectedFile}
            />
          ))}
        </div>
      )}
    </div>
  );
}

