import {useTranslation} from "react-i18next";
import {Code2, Globe, Upload} from "lucide-react";
import {useRef, useState} from "react";
import {ChatMode} from "../ChatInput";
import useChatModeStore from "@/stores/chatModeSlice";
import {UrlInputDialog} from "../UrlInputDialog";

interface TipsProps {
  setInput: (s: string) => void;
  append: (message: { role: 'user' | 'assistant'; content: string }) => void;
  handleFileSelect: (e: React.ChangeEvent<HTMLInputElement>) => void;
  handleSketchUpload?: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

const Tips = (props: TipsProps) => {
  const { handleFileSelect, setInput, append } = props;
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const sketchInputRef = useRef<HTMLInputElement>(null);
  const { mode, initOpen } = useChatModeStore();
  const [isUrlDialogOpen, setIsUrlDialogOpen] = useState(false);

  const handleUrlSubmit = (url: string): void => {
    append({
      role: "user",
      content: `#${url}`,
    });
  };

  return (
    <div className="flex w-full flex-col gap-3 text-gray-500 dark:text-gray-400">
      {initOpen ? (
        <div className="mx-auto w-full max-w-2xl py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 text-blue-500 dark:bg-blue-400/10 dark:text-blue-400">
              <Code2 className="h-5 w-5" />
            </div>
            <div>
              <div className="text-base font-medium text-gray-900 dark:text-gray-100">
                {t("chat.tips.title")}
              </div>
              <div className="text-sm text-gray-500 dark:text-gray-400">
                直接说你想做什么，或者上传图片、网站素材。
              </div>
            </div>
          </div>

          <div className="mt-5 flex flex-col gap-3">
            {mode === ChatMode.Builder && (
              <div className="grid grid-cols-2 gap-3">
                <button className="w-full rounded-lg border border-gray-300 px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:border-[#3a3b42] dark:hover:bg-white/[0.03]">
                  <div className="flex items-center gap-3 text-gray-300">
                    <Upload className="h-3 w-3" />
                    <span className="text-sm text-gray-700 dark:text-gray-300">
                      {t("chat.tips.uploadSketch")}
                    </span>
                  </div>
                </button>
                <button
                  className="w-full rounded-lg border border-gray-300 px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:border-[#3a3b42] dark:hover:bg-white/[0.03]"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <div className="flex items-center gap-3 text-gray-300">
                    <Upload className="h-3 w-3" />
                    <span className="text-sm text-gray-700 dark:text-gray-300">{t("chat.tips.uploadImg")}</span>
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="flex w-full flex-col gap-2 rounded-lg border border-gray-300 p-4 transition-colors dark:border-[#3a3b42]">
          <div className="flex items-center gap-2">
            <Code2 className="h-5 w-5 text-blue-500 dark:text-blue-400" />
            <span className="font-medium text-gray-900 dark:text-gray-100">
              {t("chat.tips.title")}
            </span>
          </div>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {t("chat.tips.description")}
          </p>
          <div className="flex flex-col gap-2 mt-2">
            
            <div className="flex gap-2">
              <div
                className="mt-2 mr-4 flex cursor-pointer items-center gap-2 text-xs text-gray-600 transition-colors hover:text-blue-500 dark:text-gray-400 dark:hover:text-blue-400"
                onClick={() => {
                  fileInputRef.current?.click();
                }}
              >
                <Upload className="h-4 w-4" />
                <span className="text-sm">{t("chat.tips.uploadImg")}</span>
              </div>
              <div
                className="mt-2 flex cursor-pointer items-center gap-2 text-xs text-gray-600 transition-colors hover:text-blue-500 dark:text-gray-400 dark:hover:text-blue-400"
                onClick={() => setIsUrlDialogOpen(true)}
              >
                <Globe className="h-4 w-4" />
                <span className="text-sm">{t("chat.tips.uploadWebsite")}</span>
              </div>
            </div>

            {mode === ChatMode.Builder && (
              <div className="flex flex-wrap gap-2 mt-2 text-xs">
                <span
                  onClick={() => {
                    setInput(t("chat.tips.game"));
                  }}
                  className="px-2 py-1 text-blue-500 rounded bg-blue-50 dark:bg-blue-500/20 dark:text-blue-400"
                >
                  {t("chat.tips.game")}
                </span>
                <span
                  onClick={() => {
                    setInput(t("chat.tips.hello"));
                  }}
                  className="px-2 py-1 text-blue-500 rounded bg-blue-50 dark:bg-blue-500/20 dark:text-blue-400"
                >
                  {t("chat.tips.hello")}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      <input
        ref={fileInputRef}
        type="file"
        onChange={handleFileSelect}
        className="hidden"
        multiple
        accept="image/*"
      />

      <UrlInputDialog
        isOpen={isUrlDialogOpen}
        onClose={() => setIsUrlDialogOpen(false)}
        onSubmit={handleUrlSubmit}
      />
    </div>
  );
  
};

export default Tips;
