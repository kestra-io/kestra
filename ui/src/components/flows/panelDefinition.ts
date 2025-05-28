import {h, markRaw} from "vue";

import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
import BallotOutlineIcon from "vue-material-design-icons/BallotOutline.vue";

import EditorSidebarWrapper from "../inputs/EditorSidebarWrapper.vue";
import EditorWrapper from "../inputs/EditorWrapper.vue";
import NoCodeWrapper from "../code/NoCodeWrapper.vue";
import LowCodeEditorWrapper from "../inputs/LowCodeEditorWrapper.vue";
import PluginDocumentationWrapper from "../plugins/PluginDocumentationWrapper.vue";
import BlueprintsWrapper from "../flows/blueprints/BlueprintsWrapper.vue";

export const DEFAULT_ACTIVE_TABS = ["code", "doc"]

export const EDITOR_ELEMENTS = [
    {
        button: {
            icon: markRaw(CodeTagsIcon),
            label: "Flow Code"
        },
        value: "code",
        component: () => h(EditorWrapper, {path: "Flow.yaml", name: "Flow.yaml"}),
    },
    {
        button: {
            icon: markRaw(MouseRightClickIcon),
            label: "No-code"
        },
        value: "nocode",
        component: markRaw(NoCodeWrapper),
    },
    {
        button: {
            icon: markRaw(FileTreeOutlineIcon),
            label: "Topology"
        },
        value: "topology",
        component: markRaw(LowCodeEditorWrapper),
    },
    {
        button: {
            icon: markRaw(FileDocumentIcon),
            label: "Documentation"
        },
        value: "doc",
        component: markRaw(PluginDocumentationWrapper),
    },
    {
        button: {
            icon: markRaw(DotsSquareIcon),
            label: "Files"
        },
        value: "files",
        component: markRaw(EditorSidebarWrapper),
    },
    {
        button: {
            icon: markRaw(BallotOutlineIcon),
            label: "Blueprints"
        },
        value: "blueprints",
        component: markRaw(BlueprintsWrapper),
    }
]