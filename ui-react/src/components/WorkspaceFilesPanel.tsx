import {useCallback, useState} from "react";
import {FilePreviewModal} from "./FilePreviewModal";
import {FileExplorer} from "./WeIde/components/IDEContent/FileExplorer";
import {useFileStore} from "./WeIde/stores/fileStore";

export function WorkspaceFilesPanel() {
  const [previewPath, setPreviewPath] = useState<string | null>(null);
  const setSelectedPath = useFileStore((state) => state.setSelectedPath);

  const handleFileSelect = useCallback(
    (path: string) => {
      setSelectedPath(path);
      setPreviewPath(path);
    },
    [setSelectedPath]
  );

  return (
    <div className="h-full min-h-0 w-full overflow-hidden">
      <FileExplorer onFileSelect={handleFileSelect} />
      <FilePreviewModal
        filePath={previewPath}
        onClose={() => setPreviewPath(null)}
      />
    </div>
  );
}

