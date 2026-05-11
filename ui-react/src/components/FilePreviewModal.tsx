import {useEffect, useMemo} from "react";
import hljs from "highlight.js";
import {Copy, FileCode2, X} from "lucide-react";
import {toast} from "react-toastify";
import {readWorkspaceFile} from "@/api/filesystem";
import {useFileStore} from "./WeIde/stores/fileStore";

interface FilePreviewModalProps {
  filePath: string | null;
  onClose: () => void;
}

export function FilePreviewModal({filePath, onClose}: FilePreviewModalProps) {
  const content = useFileStore((state) =>
    filePath ? state.files[filePath] : undefined
  );
  const projectRoot = useFileStore((state) => state.projectRoot);
  const updateContent = useFileStore((state) => state.updateContent);

  useEffect(() => {
    if (!filePath) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [filePath, onClose]);

  useEffect(() => {
    if (!filePath) return;

    let cancelled = false;

    const loadLatest = async () => {
      const workspacePath = projectRoot || "workspace";
      const response = await readWorkspaceFile(workspacePath, filePath);

      if (!cancelled && response.success && response.content !== undefined) {
        await updateContent(filePath, response.content, true, true);
      }
    };

    loadLatest();

    return () => {
      cancelled = true;
    };
  }, [filePath, projectRoot, updateContent]);

  const highlightedContent = useMemo(() => {
    if (content === undefined) {
      return "";
    }

    try {
      return hljs.highlightAuto(content).value;
    } catch {
      return content
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
    }
  }, [content]);

  if (!filePath) {
    return null;
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content ?? "");
      toast.success("Copied");
    } catch {
      toast.error("Copy failed");
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-5"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="File preview"
    >
      <div
        className="flex h-[82vh] w-full max-w-5xl flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-2xl dark:border-[#30323a] dark:bg-[#18181a]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex h-11 shrink-0 items-center gap-2 border-b border-gray-200 px-3 dark:border-[#30323a]">
          <FileCode2 className="h-4 w-4 text-gray-500 dark:text-gray-400" />
          <div className="min-w-0 flex-1 truncate text-sm font-medium text-gray-800 dark:text-gray-100">
            {filePath}
          </div>
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-800 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100"
            aria-label="Copy file content"
            title="Copy"
          >
            <Copy className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-800 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100"
            aria-label="Close preview"
            title="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-auto bg-[#fafafa] dark:bg-[#111214]">
          {content === undefined ? (
            <div className="flex h-full items-center justify-center text-sm text-gray-500 dark:text-gray-400">
              File content is not available.
            </div>
          ) : (
            <pre className="m-0 min-h-full overflow-visible p-4 text-xs leading-5 text-gray-900 dark:text-gray-100">
              <code dangerouslySetInnerHTML={{__html: highlightedContent}} />
            </pre>
          )}
        </div>
      </div>
    </div>
  );
}
