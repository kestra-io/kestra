import {getVSIFileIcon, getVSIFolderIcon} from "file-extension-icon-js";

type Payload = { name: string; isFolder?: boolean; isOpen?: boolean };
const FOLDER_TYPE = "folder";

export const getIcon = (payload: Payload): string | undefined => {
    if (payload.isFolder) return getVSIFolderIcon(FOLDER_TYPE, payload.isOpen);

    if (!payload.name) return; // Guard against invalid filenames
    return getVSIFileIcon(payload.name);
};
