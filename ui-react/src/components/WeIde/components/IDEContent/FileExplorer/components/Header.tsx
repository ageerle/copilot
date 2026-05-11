import {FolderTree} from "lucide-react";
import {useTranslation} from "react-i18next";

export function Header() {
  const {t} = useTranslation();

  return (
    <h2 className="mb-2 flex select-none items-center text-[13px] font-semibold uppercase text-[#424242] dark:text-gray-400">
      <FolderTree className="mr-1.5 h-4 w-4" /> {t("explorer.explorer")}
    </h2>
  );
}
