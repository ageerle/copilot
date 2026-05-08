import {ConfigProvider, theme} from "antd";
import {BaseChat} from "./chat";
import {ChatMode} from "@/types/chat";
import useChatModeStore from "@/stores/chatModeSlice";

const Independent: React.FC = () => {
  const { mode, initOpen } = useChatModeStore();


  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
      }}
    >
      <div
        className={`bg-white dark:bg-[#18181a] min-w-[420px] rounded-xl p-0 shadow-sm border border-gray-200 dark:border-[#2a2b31] ${
          initOpen ? 'flex items-center justify-center' : ''
        }`}
        style={{
          width: `${mode === ChatMode.Builder && !initOpen ? "380px" : "100%"}`,
        }}
      >
        <div className="h-full w-full rounded-xl overflow-hidden">
          <BaseChat />
        </div>
      </div>
    </ConfigProvider>
  );
};
export default Independent;
