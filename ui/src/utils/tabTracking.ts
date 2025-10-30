import {useApiStore} from "../stores/api";
import {usePluginsStore} from "../stores/plugins";
import {useBlueprintsStore} from "../stores/blueprints";
import {useMiscStore} from "override/stores/misc";
import {Tab} from "./multiPanelTypes";

interface TrackedTab extends Tab {
    potential?: boolean
    fromPanel?: boolean
}

export function getTabType(tab: TrackedTab): string {
    const value = tab.uid;

    if (value.startsWith("nocode-")) {
        try {
            const tabData = JSON.parse(value.substring(12));
            const parentPath = tabData.parentPath || "";
            const mapping: [string, string][] = [
                ["tasks", "task_no_code"],
                ["triggers", "trigger_no_code"],
                ["pluginDefaults", "pluginDefaults_no_code"],
                ["errors", "error_no_code"],
                ["finally", "finally_no_code"],
                ["afterExecution", "afterExecution_no_code"],
            ];
            for (const [k, v] of mapping) {
                if (parentPath.includes(k)) return v;
            }
            return "task_no_code";
        } catch {
            //
        }
    }

    switch (value) {
    case "code":
        return "flow_code";
    case "nocode":
        return "flow_no_code";
    case "topology":
        return "topology";
    case "doc":
        return "documentation";
    case "blueprints":
        return "blueprint";
    case "files":
        return "files_browser";
    default:
        return "flow_code";
    }
}

export function getTabMetadata(tab: TrackedTab): Record<string, any> {
    const metadata: Record<string, any> = {};
    const value = tab.uid;

    if (value === "doc") {
        const pluginsStore = usePluginsStore();
        if (pluginsStore.editorPlugin?.cls) {
            metadata.documentation_page = pluginsStore.editorPlugin.cls;
        }
    }

    if (value === "blueprints") {
        const blueprintsStore = useBlueprintsStore();
        if (blueprintsStore.blueprint?.id) {
            metadata.blueprint_name = blueprintsStore.blueprint.id;
        }
    }

    if (value.startsWith("nocode-")) {
        try {
            const tabData = JSON.parse(value.substring(12));
            if (tabData.taskType) {
                metadata.task_type = tabData.taskType;
            }
        } catch {
            // Ignore parsing errors
        }
    }

    return metadata;
}

function sendTrackingEvent(eventData: any) {
    try {
        const apiStore = useApiStore();
        const miscStore = useMiscStore();

        // Check if analytics is enabled
        if (miscStore.configs?.isAnonymousUsageEnabled === false) {
            return;
        }

        const sendingData = {
            ...eventData,
            iid: miscStore.configs?.uuid,
            uid: localStorage.getItem("uid"),
            date: new Date().toISOString(),
        };

        // Send to backend with PAGE type and editor_tab structure
        const backendData = {
            ...sendingData,
            type: "PAGE",
            editor_tab: {
                action: eventData.action,
                tab_type: eventData.tab_type,
                ...eventData.metadata
            }
        };

        delete backendData.action;
        delete backendData.tab_type;
        delete backendData.metadata;

        apiStore.events(backendData);

        // Send to PostHog via API store (handles PostHog initialization internally)
        const posthogData = {
            ...sendingData,
            type: "EDITOR_TAB_ACTION"
        };
        apiStore.posthogEvents(posthogData);
    } catch {
        //
    }
}

function makeEvent(action: string, tab_type: string, metadata?: Record<string, any>) {
    sendTrackingEvent({
        action,
        tab_type,
        metadata: metadata ?? {}
    });
}

export function trackTabOpen(tab: TrackedTab) {
    makeEvent("open", getTabType(tab), getTabMetadata(tab));
}

export function trackTabClose(tab: TrackedTab) {
    makeEvent("close", getTabType(tab), getTabMetadata(tab));
}

export function trackBlueprintSelection(blueprintId: string) {
    makeEvent("blueprint_selection", "blueprint", {blueprint_name: blueprintId});
}

export function trackPluginDocumentationView(pluginClass: string) {
    makeEvent("plugin_doc", "documentation", {documentation_page: pluginClass});
}

export function trackFileOpen(fileName: string) {
    makeEvent("files_open", "files_browser", {file_name: fileName});
}
