import {ConfigProvider, theme} from "antd";
import {BaseChat} from "./chat";
import useChatModeStore from "@/stores/chatModeSlice";

const Independent: React.FC = () => {
  const { initOpen } = useChatModeStore();


  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
      }}
    >
      <div
        className={`h-full w-full min-w-0 p-0 ${
          initOpen ? "flex items-center justify-center" : ""
        }`}
      >
        <div className="h-full w-full overflow-hidden">
          <BaseChat />
        </div>
      </div>
    </ConfigProvider>
  );
};
export default Independent;
