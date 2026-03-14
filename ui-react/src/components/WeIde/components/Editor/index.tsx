import {useFileStore} from "../../stores/fileStore";
import {useEditorStore} from "../../stores/editorStore";
import {useEditorSetup} from "./hooks/useEditorSetup";
import {useEditorScroll} from "./hooks/useEditorScroll";
import {useStreamingStatus} from "../../hooks/useStreamingStatus";
import "./styles/diff.css";

interface EditorProps {
  fileName: string;
  initialLine?: number;
}

export const Editor = ({ fileName, initialLine }: EditorProps) => {
  const { getContent } = useFileStore();
  const { setDirty, setCurrentFile } = useEditorStore();
  const streamingStatus = useStreamingStatus(fileName);

  const rawContent = getContent(fileName);

  const handleDocChange = () => {
    setCurrentFile(fileName);
    setDirty(fileName, true);
  };

  const { editorRef, viewRef } = useEditorSetup({
    fileName,
    fileContent: rawContent,
    onDocChange: handleDocChange,
  });

  useEditorScroll({
    view: viewRef.current,
    fileContent: rawContent,
  });

  return (
    <div className="relative h-full w-full">
      {/* 流式输出状态提示 */}
      {streamingStatus === 'waiting' && (
        <div className="absolute top-2 right-2 z-10 px-3 py-1.5 bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 text-xs rounded-md shadow-sm flex items-center gap-2">
          <span className="animate-pulse">●</span>
          等待后端推送...
        </div>
      )}
      {streamingStatus === 'streaming' && (
        <div className="absolute top-2 right-2 z-10 px-3 py-1.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-xs rounded-md shadow-sm flex items-center gap-2">
          <span className="animate-pulse">●</span>
          正在生成代码...
        </div>
      )}

      <div
        ref={editorRef}
        className={`
          editor-container h-full w-full overflow-hidden
          [&_.cm-editor]:!bg-[#ffffff] [&_.cm-editor]:dark:!bg-[#18181a]
          [&_.cm-scroller]:!font-mono
          /* 行号和边栏区域 */
          [&_.cm-gutters]:!bg-[#f5f5f5] [&_.cm-gutters]:dark:!bg-[#18181a]
          [&_.cm-gutters]:border-r [&_.cm-gutters]:border-[#e5e5e5] [&_.cm-gutters]:dark:border-[#3c3c3c]
          [&_.cm-lineNumbers]:!text-[#237893] [&_.cm-lineNumbers]:dark:!text-[#c5c5c5]
          [&_.cm-gutterElement]:pl-[10px] [&_.cm-gutterElement]:min-w-[40px]

          /* 活动行高亮 */
          [&_.cm-activeLine]:!bg-[#f6f6f6] [&_.cm-activeLine]:dark:!bg-[#2c2c2c]
          [&_.cm-activeLineGutter]:!bg-[#f6f6f6] [&_.cm-activeLineGutter]:dark:!bg-[#2c2c2c]

          /* 选择和搜索 */
          [&_.cm-selectionBackground]:!bg-[#add6ff80] [&_.cm-selectionBackground]:dark:!bg-[#3a6da0]
          [&_.cm-selectionMatch]:!bg-[#c9d0d988] [&_.cm-selectionMatch]:dark:!bg-[#4e4e4e]
          [&_.cm-searchMatch]:!bg-[#ffd70033] [&_.cm-searchMatch]:dark:!bg-[#6b8caf]
          [&_.cm-searchMatch-selected]:!bg-[#ffa50080]

          /* 基础文本和光标 */
          [&_.cm-cursor]:!border-l-[2px] [&_.cm-cursor]:!border-l-solid [&_.cm-cursor]:!border-l-[#ff0000] [&_.cm-cursor]:dark:!border-l-[#ff0000]

          /* 特殊元素 */
          [&_.cm-matchingBracket]:!bg-[#c9d0d988] [&_.cm-matchingBracket]:dark:!bg-[#4e4e4e]
          [&_.cm-matchingBracket]:!border-[#569cd6]
          [&_.cm-nonmatchingBracket]:!border-[#cd3131]
          [&_.cm-foldPlaceholder]:!bg-[#f5f5f5] [&_.cm-foldPlaceholder]:dark:!bg-[#2d2d2d]
          [&_.cm-tooltip]:!bg-white [&_.cm-tooltip]:dark:!bg-[#1a1a1c]
          [&_.cm-tooltip]:border-[#e5e5e5] [&_.cm-tooltip]:dark:border-[#454545]

          /* 语法高亮基础样式 */
          [&_.cm-line]:!text-[#000000] [&_.cm-line]:dark:!text-[#e4e4e4]
        `}
        role="textbox"
        aria-label={`Code editor for ${fileName}`}
        tabIndex={0}
      />
    </div>
  );
};
