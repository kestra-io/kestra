import {storageKeys} from "../../utils/constants"

export function getDefaultActiveTabs(): string[] {
    const mainTab = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE" ? "nocode" : "code"
    const openDocs = localStorage.getItem(storageKeys.EDITOR_OPEN_DOCS_TAB) !== "false"
    return openDocs ? [mainTab, "doc"] : [mainTab]
}
