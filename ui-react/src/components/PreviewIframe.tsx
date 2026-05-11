import {Dispatch, SetStateAction, useEffect, useRef, useState} from "react";
import {getContainerInstance} from "./WeIde/services";
import {ChevronDown, Laptop, Monitor, Smartphone, Tablet} from "lucide-react";
import {findWeChatDevToolsPath} from "./EditorPreviewTabs";
import {useFileStore} from "./WeIde/stores/fileStore";
import {useTranslation} from "react-i18next";

interface PreviewIframeProps {
  setShowIframe: Dispatch<SetStateAction<string>>;
  isMinPrograme: boolean;
}
interface WindowSize {
  name: string;
  width: number | string;
  height: number | string;
  icon: React.ComponentType<{ size?: string | number }>;
}
const WINDOW_SIZES: WindowSize[] = [
  { name: "Desktop", width: '100%', height:'100%', icon: Monitor },
  { name: "Mobile", width: 375, height: 667, icon: Smartphone },
  {
    name: "Tablet",
    width: Number((768 / 1.5).toFixed(0)),
    height: Number((1024 / 1.5).toFixed(0)),
    icon: Tablet,
  },
  { name: "Laptop", width: 1366, height: 768, icon: Laptop },
];

const PreviewIframe: React.FC<PreviewIframeProps> = ({
  setShowIframe,
  isMinPrograme,
}) => {
  const [url, setUrl] = useState<string>("");
  const [port, setPort] = useState<string>("");
  const { projectRoot } = useFileStore();
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [scale, setScale] = useState<number>(1);
  const { t } = useTranslation();
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedSize, setSelectedSize] = useState<WindowSize>(WINDOW_SIZES[0]);
  const [isWindowSizeDropdownOpen, setIsWindowSizeDropdownOpen] = useState(false);
  const [iframeLoaded, setIframeLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const instance = await getContainerInstance();
        if (instance) {
          instance.on("server-ready", (port, url) => {
            console.log("server-ready", port, url);
            setUrl(url);
            setShowIframe("preview");
            setPort(port.toString());
          });
        } else {
          console.warn("Container instance not available");
        }
      } catch (error) {
        console.error("Failed to get container instance:", error);
      }
    })();

  }, []);

  const handleRefresh = () => {
    console.log("刷新 handleRefresh", iframeRef.current);
    if (iframeRef.current) {
      iframeRef.current.src = iframeRef.current.src;
    }
  };

    const displayUrl = port
    ? `http://localhost:${port}`
    : isMinPrograme
      ? t('preview.wxminiPreview')
      : t('preview.noserver');

  const handleWheel = (e: WheelEvent) => {
    if (e.ctrlKey) {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setScale((prevScale) => {
        const newScale = prevScale * delta;
        return Math.min(Math.max(newScale, 0.5), 3);
      });
    }
  };

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let initialDistance = 0;
    let initialScale = 1;

    const handleTouchStart = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        e.preventDefault();
        initialDistance = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        initialScale = scale;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        e.preventDefault();
        const distance = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        const delta = distance / initialDistance;
        const newScale = Math.min(Math.max(initialScale * delta, 0.5), 3);
        setScale(newScale);
      }
    };

    container.addEventListener("wheel", handleWheel, { passive: false });
    container.addEventListener("touchstart", handleTouchStart);
    container.addEventListener("touchmove", handleTouchMove);

    return () => {
      container.removeEventListener("wheel", handleWheel);
      container.removeEventListener("touchstart", handleTouchStart);
      container.removeEventListener("touchmove", handleTouchMove);
    };
  }, [scale]);

  const handleZoomIn = () => {
    setScale((prevScale) => Math.min(prevScale * 1.1, 3));
  };

  const handleZoomOut = () => {
    setScale((prevScale) => Math.max(prevScale * 0.9, 0.5));
  };

  const handleZoomReset = () => {
    setScale(1);
  };

  const openExternal = () => {
    const externalUrl = port ? `http://localhost:${port}/` : "http://localhost:5174/";
    window.electron.ipcRenderer.send(
      "open:external:url",
      externalUrl
    );
  };

  useEffect(() => {
    if (iframeLoaded && iframeRef.current?.contentWindow) {
      try {
        // Check if we can access the iframe's document (same-origin)
        const iframeWindow = iframeRef.current.contentWindow;

        // Test access to document - this will throw if cross-origin
        const testAccess = iframeWindow.document;

        const injectScript = `

        `;

        const script = iframeWindow.document.createElement('script');
        script.textContent = injectScript;
        iframeWindow.document.head.appendChild(script);

      } catch (error) {
        // Silently handle cross-origin access errors
        // This is expected when the iframe content is from a different origin
        if (error instanceof DOMException && error.name === 'SecurityError') {
          console.warn('Cannot inject script into iframe due to cross-origin restrictions');
        } else {
          console.error('Unexpected error accessing iframe:', error);
        }
      }
    }
  }, [iframeLoaded]);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.data.type === 'REQUEST_BLOB_ACCESS') {
        const blobUrl = event.data.blobUrl;
        const requestId = event.data.requestId;


        fetch(blobUrl)
          .then(response => {
            if (!response.ok) {
              throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.blob();
          })
          .then(blob => {
            const reader = new FileReader();
            reader.onloadend = function() {
              const base64data = reader.result as string;
              const base64Content = base64data.split(',')[1];
              if (iframeRef.current?.contentWindow) {
                try {
                  iframeRef.current.contentWindow.postMessage({
                    type: 'BLOB_ACCESS_GRANTED',
                    blobData: base64Content,
                    contentType: blob.type,
                    originalUrl: blobUrl,
                    requestId: requestId
                  }, '*');
                } catch (error) {
                  console.warn('Failed to send message to iframe:', error);
                }
              }
            };
            reader.onerror = function() {
              console.error('Failed to read blob as data URL');
            };
            reader.readAsDataURL(blob);
          })
          .catch(error => {
            console.error('Failed to fetch blob:', error);
            // Notify iframe about the error
            if (iframeRef.current?.contentWindow) {
              try {
                iframeRef.current.contentWindow.postMessage({
                  type: 'BLOB_ACCESS_ERROR',
                  error: error.message,
                  requestId: requestId
                }, '*');
              } catch (postError) {
                console.warn('Failed to send error message to iframe:', postError);
              }
            }
          });
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, []);

  return (
    <div className="preview-container w-full h-full relative flex flex-col overflow-hidden">
      <div
        ref={containerRef}
        className="flex-1 relative bg-white overflow-hidden rounded-b-lg flex items-center justify-center"
        style={{
          cursor: isDragging ? "grabbing" : "grab",
        }}
      >
        <div
          className="bg-white transition-all duration-200 origin-center"
          style={{
            width: String(selectedSize?.width)?.indexOf('%') > -1 ?  `${(Number.parseFloat(String(selectedSize.width)) * ((1 / scale)))}%`  : `${(Number(selectedSize.width) * (1 / scale))}px`,
            height: String(selectedSize?.height)?.indexOf('%') > -1 ?`${(Number.parseFloat(String(selectedSize.height)) * (1 / scale))}%`  : `${(Number(selectedSize.height) * (1 / scale))}px`,
            transform: `scale(${scale})`,
          }}
        >
          <iframe
            ref={iframeRef}
            src={url}
            className="w-full h-full border-none rounded-b-lg bg-white"
            style={{
              width: '100%',
              minHeight: "400px",
            }}
            title="preview"
            sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
            allow="cross-origin-isolated"
            onLoad={() => {
              console.log("Iframe loaded successfully");
              setIframeLoaded(true);
            }}
            onError={(e) => {
              console.error("Iframe failed to load:", e);
              setIframeLoaded(false);
            }}
          />
        </div>
        {isMinPrograme && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-50">
            <div className="text-gray-400">{t("preview.wxminiPreview")}</div>
          </div>
        )}
        {!url && !isMinPrograme && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-50">
            <div className="text-gray-400">{t("preview.noserver")}</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PreviewIframe;
