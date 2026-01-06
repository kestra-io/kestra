import {h, markRaw} from "vue";
import {storageKeys} from "../../../utils/constants";

import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue";

import NoCode from "../../../components/no-code/NoCode.vue";
import EditorWrapper from "../../../components/inputs/EditorWrapper.vue";
import PluginListWrapper from "../../../components/plugins/PluginListWrapper.vue";
import LowCodeEditorWrapper from "../../../components/inputs/LowCodeEditorWrapper.vue";
import FileExplorerWrapper from "../../../components/inputs/FileExplorerWrapper.vue";
import BlueprintsWrapper from "../../../components/flows/blueprints/BlueprintsWrapper.vue";
import {EditorElement} from "../../../utils/multiPanelTypes";

export const DEFAULT_ACTIVE_TABS = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE" ? ["nocode", "doc"] : ["code", "doc"]

export const EDITOR_ELEMENTS: EditorElement[] = [
    {
        button: {
            icon: markRaw(CodeTagsIcon),
            label: "Flow Code"
        },
        uid: "code",
        component: () => h(EditorWrapper, {
            path: "Flow.yaml",
            name: "Flow.yaml",
            dirty: false,
            extension: "yaml",
            flow: true,
        }),
    },
    {
        button: {
            icon: markRaw(MouseRightClickIcon),
            label: "No-code"
        },
        uid: "nocode",
        component: markRaw(NoCode),
    },
    {
        button: {
            icon: markRaw(FileTreeOutlineIcon),
            label: "Topology"
        },
        uid: "topology",
        component: markRaw(LowCodeEditorWrapper),
    },
    {
        button: {
            icon: markRaw(FileDocumentIcon),
            label: "Documentation"
        },
        uid: "doc",
        component: markRaw(PluginListWrapper),
    },
    {
        button: {
            icon: markRaw(FolderOpenOutline),
            label: "Files"
        },
        uid: "files",
        prepend: true,
        component: markRaw(FileExplorerWrapper),
    },
    {
        button: {
            icon: markRaw(ShapePlusOutline),
            label: "Blueprints"
        },
        uid: "blueprints",
        component: markRaw(BlueprintsWrapper),
    }
].map((e): EditorElement => ({
    // add a default deserializer
    deserialize: (value: string) => {
        if (e.uid === value) {
            return e;
        }
        return undefined;
    },
    ...e,
}));
