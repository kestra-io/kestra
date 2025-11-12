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
import {EditorElement} from "../../../utils/multiPanelTypes";

export const DEFAULT_ACTIVE_TABS = ["code", "doc"];

// code, nocode, doc, charts, preview
export const DASHBOARD_EDITOR_ELEMENTS = [
    {
        button: {
            icon: markRaw(CodeTagsIcon),
            label: "Code"
        },
        uid: "code",
        component: markRaw(DashboardCodeEditor),
    },
    {
        button: {
            icon: markRaw(DotsSquareIcon),
            label: "No Code"
        },
        uid: "nocode",
        component: markRaw(DashboardNoCodeEditor),
    },
    {
        button: {
            icon: markRaw(FileDocumentIcon),
            label: "Documentation"
        },
        uid: "doc",
        component: () => h(PluginDocumentationWrapper, {overrideIntro: intro, absolute: true}),
    },
    {
        button: {
            icon: markRaw(ChartBarIcon),
            label: "Charts"
        },
        uid: "charts",
        component: markRaw(ChartViewWrapper),
    },
    {
        button: {
            icon: markRaw(ViewDashboardIcon),
            label: "Preview"
        },
        uid: "preview",
        component: markRaw(PreviewDashboardWrapper),
    }
].map((e): EditorElement => ({
    // add a default deserializer
    deserialize: (value: string) => {
        if(e.uid === value){
            return e;
        }
        return undefined;
    },
    ...e,
}));