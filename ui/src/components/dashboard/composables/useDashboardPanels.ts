import {markRaw, h} from "vue";
import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import ChartBarIcon from "vue-material-design-icons/ChartBar.vue";
import ViewDashboardIcon from "vue-material-design-icons/ViewDashboard.vue";
import DashboardCodeEditor from "../components/DashboardCodeEditor.vue";
import PluginDocumentationWrapper from "../../plugins/PluginDocumentationWrapper.vue";
import ChartViewWrapper from "../components/ChartViewWrapper.vue";
import PreviewDashboardWrapper from "../components/PreviewDashboardWrapper.vue";

import intro from "../../../assets/docs/dashboard_home.md?raw";
import DashboardNoCodeEditor from "../components/DashboardNoCodeEditor.vue";
import {Tab} from "../../../utils/multiPanelTypes";

interface EditorElement {
    button: {
        icon: any,
        label: string
    },
    value: string,
    component: any,
    deserialize?: (value: string) => Tab | undefined
}

interface DeserializableEditorElement extends EditorElement {
    deserialize: (value: string) => Tab | undefined
}

export const DEFAULT_ACTIVE_TABS = ["code", "doc"];

// code, nocode, doc, charts, preview
export const DASHBOARD_EDITOR_ELEMENTS = [
    {
        button: {
            icon: markRaw(CodeTagsIcon),
            label: "Code"
        },
        value: "code",
        component: markRaw(DashboardCodeEditor),
    },
    {
        button: {
            icon: markRaw(DotsSquareIcon),
            label: "No Code"
        },
        value: "nocode",
        component: markRaw(DashboardNoCodeEditor),
    },
    {
        button: {
            icon: markRaw(FileDocumentIcon),
            label: "Documentation"
        },
        value: "doc",
        component: () => h(PluginDocumentationWrapper, {overrideIntro: intro, absolute: true}),
    },
    {
        button: {
            icon: markRaw(ChartBarIcon),
            label: "Charts"
        },
        value: "charts",
        component: markRaw(ChartViewWrapper),
    },
    {
        button: {
            icon: markRaw(ViewDashboardIcon),
            label: "Preview"
        },
        value: "preview",
        component: markRaw(PreviewDashboardWrapper),
    }
].map((e): DeserializableEditorElement => ({
    // add a default deserializer
    deserialize: (value: string) => {
        if(e.value === value){
            return e;
        }
        return undefined;
    },
    ...e,
}));