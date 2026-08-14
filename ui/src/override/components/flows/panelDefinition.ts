import {defineAsyncComponent, h, markRaw} from "vue"
import {storageKeys} from "../../../utils/constants"

import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue"
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue"
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue"
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue"
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue"

import FlowFileEditorTab from "../../../components/inputs/FlowFileEditorTab.vue"
import PluginListWrapper from "../../../components/plugins/PluginListWrapper.vue"
import {EditorElement} from "../../../utils/multiPanelTypes"

// `doc` always opens on arrival, and `code`'s editor tab sits in the boot graph either way
// since useFilesPanels imports it for its injection keys - so deferring those two would buy a
// round trip and nothing else.
//
// The rest are behind a tab, yet importing them here dragged their whole subtree into the boot
// graph regardless: topology pulls @vue-flow/core and @kestra-io/topology, blueprints pulls the
// blueprint browser and its stores, files pulls the file explorer. `nocode` is deferred too,
// even though the editor-view-type setting can make it a boot tab, because the engine that
// actually renders no-code tabs is loaded on demand anyway (see RawNoCode in
// MultiPanelFlowEditorView).
const NoCode = markRaw(defineAsyncComponent(() => import("../../../components/no-code/NoCode.vue")))
const LowCodeEditorWrapper = markRaw(defineAsyncComponent(() => import("../../../components/inputs/LowCodeEditorWrapper.vue")))
const FileExplorerWrapper = markRaw(defineAsyncComponent(() => import("../../../components/inputs/FileExplorerWrapper.vue")))
const BlueprintsWrapper = markRaw(defineAsyncComponent(() => import("../../../components/flows/blueprints/BlueprintsWrapper.vue")))

export const DEFAULT_ACTIVE_TABS = localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE" ? ["nocode", "doc"] : ["code", "doc"]

export const EDITOR_ELEMENTS: EditorElement[] = [
    {
        button: {
            icon: markRaw(CodeTagsIcon),
            label: "Flow Code",
        },
        uid: "code",
        component: () => h(FlowFileEditorTab, {
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
            label: "No-code",
        },
        uid: "nocode",
        component: NoCode,
    },
    {
        button: {
            icon: markRaw(FileTreeOutlineIcon),
            label: "Topology",
        },
        uid: "topology",
        component: LowCodeEditorWrapper,
    },
    {
        button: {
            icon: markRaw(FileDocumentIcon),
            label: "Docs",
        },
        uid: "doc",
        component: markRaw(PluginListWrapper),
    },
    {
        button: {
            icon: markRaw(FolderOpenOutline),
            label: "Files",
        },
        uid: "files",
        prepend: true,
        component: FileExplorerWrapper,
    },
    {
        button: {
            icon: markRaw(ShapePlusOutline),
            label: "Blueprints",
        },
        uid: "blueprints",
        component: BlueprintsWrapper,
    },
].map((e): EditorElement => ({
    // add a default deserializer
    deserialize: (value: string) => {
        if (e.uid === value) {
            return e
        }
        return undefined
    },
    ...e,
}))
