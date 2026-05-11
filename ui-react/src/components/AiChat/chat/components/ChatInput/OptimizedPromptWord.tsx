import {useEffect, useRef, useState} from "react";
import {useTranslation} from 'react-i18next';
import { apiUrl } from "@/api/base";

interface PromptEnhancedProps {
  setInput: (text: string) => void;
  input: string
}
const PromptEnhanced = (props: PromptEnhancedProps) => {
  const { setInput, input } = props || {};
  const [isOpen, setIsOpen] = useState(false);
  const [promptText, setPromptText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);
  const { t } = useTranslation();
  useEffect(() => {
    if (isOpen) {
      setPromptText(input);
    }
  }, [isOpen]);
  const handleClick = async () => {
    setIsLoading(true);
    try {
      const res = await fetch(apiUrl('/api/enhancedPrompt'), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          prompt: promptText,
        }),
      });
      const r = await res.json();
      // 后端返回的字段是 enhancedPrompt
      setInput(r.enhancedPrompt || r.text || promptText);
      setIsOpen(false);
    } catch (error) {
      console.error(t('chat.optimizePrompt.error'), error);
    } finally {
      setIsLoading(false);
    }
  };
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        popoverRef.current &&
        !popoverRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="relative">
      {isOpen ? (
        <div
          className={`absolute left-0 bottom-full mb-2 w-96 rounded-lg border border-gray-200/80 bg-white p-4 shadow-xl transition-all duration-200 ease-in-out transform origin-bottom dark:border-[#2a2b31] dark:bg-[#18181a]
          ${
            isOpen
              ? "opacity-100 translate-y-0 scale-100"
              : "opacity-0 translate-y-2 scale-95 pointer-events-none"
          }`}
          ref={popoverRef}
        >
          <h3 className="text-sm font-medium mb-2 text-gray-700 dark:text-gray-200">
            {t('chat.optimizePrompt.title')}
          </h3>
          <textarea
            className="h-32 w-full resize-none rounded-lg border border-gray-200/80 bg-transparent p-2.5 text-xs transition-colors duration-200 focus:border-blue-400 focus:outline-none dark:border-[#2a2b31] dark:text-gray-300"
            value={promptText}
            onChange={(e) => setPromptText(e.target.value)}
            placeholder={t('chat.optimizePrompt.placeholder')}
            onKeyDown={async (e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                await handleClick();
              }
            }}
          />
          <div className="flex justify-end gap-2 mt-3">
            <button
              className="px-2.5 py-1.5 text-xs text-gray-600 hover:text-gray-700 dark:text-gray-300 dark:hover:text-gray-100 transition-colors duration-200"
              onClick={() => setIsOpen(false)}
              disabled={isLoading}
            >
              {t('chat.optimizePrompt.cancel')}
            </button>
            <button
              className={`px-3 py-1.5 text-xs text-white bg-blue-500/90 rounded hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800 transition-all duration-200 ${
                isLoading ? "opacity-70 cursor-not-allowed" : ""
              }`}
              onClick={handleClick}
              disabled={isLoading}
            >
              {isLoading ? (
                <span className="flex items-center gap-1">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                      fill="none"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  {t('chat.optimizePrompt.processing')}
                </span>
              ) : (
                t('chat.optimizePrompt.confirm')
              )}
            </button>
          </div>
        </div>
      ) : null}
      <div
        className="mb-2 flex w-fit cursor-pointer items-center gap-1 rounded-md px-1.5 py-1 text-xs text-gray-500 transition-colors duration-200 ease-in-out hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        onClick={() => {
          setPromptText(input)
          setIsOpen(!isOpen);
        }}
      >
        <svg
          className="h-3.5 w-3.5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
          />
        </svg>
        {t('chat.optimizePrompt.button')}
      </div>
    </div>
  );
};

export default PromptEnhanced;
