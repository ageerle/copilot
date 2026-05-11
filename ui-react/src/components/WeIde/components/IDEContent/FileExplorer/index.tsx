import {useState} from "react";
import {FileList} from "./components/FileList";
import {Header} from "./components/Header";
import {FolderContextMenu} from "./components/FolderContextMenu";
import {CreateDialog} from "./components/CreateDialog";
import {createFile, createFolder} from "./utils/fileSystem";
import {FileExplorerProps} from "./types";
import {useFileStore} from "@/components/WeIde/stores/fileStore";

export function FileExplorer({onFileSelect}: FileExplorerProps) {
  const [contextMenu, setContextMenu] = useState<{x: number; y: number} | null>(null);
  const [createDialog, setCreateDialog] = useState<"file" | "folder" | null>(null);
  const files = useFileStore((state) => state.files);

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    if (e.currentTarget === e.target) {
      setContextMenu({x: e.clientX, y: e.clientY});
    }
  };

  const handleCreateFile = async (name: string) => {
    const newPath = await createFile("", name);
    onFileSelect(newPath);
  };

  const handleCreateFolder = async (name: string) => {
    createFolder("", name);
  };

  return (
    <div
      className="flex h-full w-full flex-col border-r border-[#e4e4e4] bg-[#f6f6f6] dark:border-[#252525] dark:bg-[#1a1a1c]"
      onContextMenu={handleContextMenu}
    >
      <div className="flex-shrink-0 p-2 text-[#424242] dark:text-white">
        <Header />
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto bg-[#f6f6f6] dark:bg-[#1a1a1c]">
        <FileList files={files} onFileSelect={onFileSelect} />
      </div>

      {contextMenu && (
        <div className="fixed inset-0 z-50" onClick={() => setContextMenu(null)}>
          <FolderContextMenu
            x={contextMenu.x}
            y={contextMenu.y}
            path=""
            onClose={() => setContextMenu(null)}
            onRename={() => {}}
            onDelete={() => {}}
            onCreateFile={() => setCreateDialog("file")}
            onCreateFolder={() => setCreateDialog("folder")}
          />
        </div>
      )}

      <CreateDialog
        type={createDialog || "file"}
        isOpen={createDialog !== null}
        path=""
        onSubmit={createDialog === "file" ? handleCreateFile : handleCreateFolder}
        onClose={() => setCreateDialog(null)}
      />
    </div>
  );
}

