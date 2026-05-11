import useUserStore from "./stores/userSlice";
import useChatModeStore from "./stores/chatModeSlice";
import useWorkspaceStore from "./stores/workspaceSlice";
import {GlobalLimitModal} from "./components/UserModal";
import Header from "./components/Header";
import AiChat from "./components/AiChat";
import Login from "./components/Login";
import {WorkspaceFilesPanel} from "./components/WorkspaceFilesPanel";
import "./utils/i18";
import classNames from "classnames";
import {ChatMode} from "./types/chat";
import {ToastContainer} from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import useInit from "./hooks/useInit";
import {Loading} from "./components/loading";
import TopViewContainer from "./components/TopView";
import {useEffect} from "react";
import {Panel, PanelGroup, PanelResizeHandle} from "react-resizable-panels";

function App() {
    const {mode, initOpen} = useChatModeStore();

    const {isLoginModalOpen, closeLoginModal, openLoginModal, user, isAuthenticated} = useUserStore();

    const {fetchWorkspaceFiles} = useWorkspaceStore();

    const {isDarkMode} = useInit();
    const showWorkspaceFiles = mode === ChatMode.Builder && !initOpen;

    // Fetch workspace files after authentication.
    useEffect(() => {
        if (isAuthenticated && user) {
            fetchWorkspaceFiles();
        }
    }, [isAuthenticated, user, fetchWorkspaceFiles]);

    return (
        <TopViewContainer>
            <GlobalLimitModal onLogin={openLoginModal}/>
            <Login isOpen={isLoginModalOpen} onClose={closeLoginModal}/>
            <div
                className={classNames(
                    "h-screen w-screen flex flex-col overflow-hidden",
                    {
                        dark: isDarkMode,
                    }
                )}
            >
                <Header/>
                <div
                    className="flex w-full h-full max-h-[calc(100%-48px)] bg-[#f5f6f8] dark:bg-[#101114]"
                >
                    {showWorkspaceFiles ? (
                        <PanelGroup direction="horizontal" className="h-full w-full">
                            <Panel
                                defaultSize={72}
                                minSize={55}
                                className="h-full min-w-0 overflow-hidden p-2 pr-1"
                            >
                                <AiChat/>
                            </Panel>
                            <PanelResizeHandle className="w-1 cursor-col-resize bg-transparent transition-colors hover:bg-gray-300 dark:hover:bg-[#333842]" />
                            <Panel
                                defaultSize={28}
                                minSize={20}
                                maxSize={40}
                                className="h-full min-w-[260px] overflow-hidden p-2 pl-1"
                            >
                                <WorkspaceFilesPanel/>
                            </Panel>
                        </PanelGroup>
                    ) : (
                        <div className="h-full w-full p-2">
                            <AiChat/>
                        </div>
                    )}
                </div>
            </div>
            <ToastContainer
                position="top-center"
                autoClose={2000}
                hideProgressBar={false}
                newestOnTop={false}
                closeOnClick
                rtl={false}
                pauseOnFocusLoss
                draggable
                pauseOnHover
                theme="colored"
                style={{
                    zIndex: 100000,
                }}
            />
            <Loading/>
        </TopViewContainer>
    );
}

export default App;
