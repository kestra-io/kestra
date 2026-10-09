import {useApiStore} from "../stores/api"
import {useMiscStore} from "override/stores/misc"
import {Tab} from "./multiPanelTypes"
import {storageKeys} from "./constants"

export interface TrackedTab extends Tab {
    potential?: boolean
    fromPanel?: boolean
}

export function getTabType(tab: TrackedTab): string {
    const value = tab.uid

    if (value.startsWith("nocode-")) {
        try {
            const tabData = JSON.parse(value.substring(12))
            const parentPath = tabData.parentPath || ""
            const mapping: [string, string][] = [
                ["tasks", "task_no_code"],
                ["triggers", "trigger_no_code"],
                ["errors", "error_no_code"],
                ["finally", "finally_no_code"],
                ["afterExecution", "afterExecution_no_code"],
            ]
            for (const [k, v] of mapping) {
                if (parentPath.includes(k)) return v
            }
            return "task_no_code"
        } catch {
            //
        }
    }

    switch (value) {
    case "code":
        return "flow_code"
    case "nocode":
        return "flow_no_code"
    case "topology":
        return "topology"
    case "doc":
        return "documentation"
    case "blueprints":
        return "blueprint"
    case "files":
        return "files_browser"
    default:
        return "flow_code"
    }
}

function sendTrackingEvent(eventData: any) {
    try {
        const apiStore = useApiStore()
        const miscStore = useMiscStore()

        // Check if analytics is enabled
        if (miscStore.configs?.isAnonymousUsageEnabled === false) {
            return
        }

        const sendingData = {
            ...eventData,
            iid: miscStore.configs?.uuid,
            uid: localStorage.getItem(storageKeys.UID),
            date: new Date().toISOString(),
        }

        // Send to backend with PAGE type and editor_tab structure
        const backendData = {
            ...sendingData,
            type: "PAGE",
            editor_tab: {
                action: eventData.action,
                tab_type: eventData.tab_type,
                ...eventData.metadata,
            },
        }

        delete backendData.action
        delete backendData.tab_type
        delete backendData.metadata

        apiStore.events(backendData, {posthog: false})

        // Send to PostHog via API store (handles PostHog initialization internally)
        const posthogData = {
            ...sendingData,
            type: "EDITOR_TAB_ACTION",
        }
        apiStore.posthogEvents(posthogData)
    } catch {
        //
    }
}

export function makeEvent(action: string, tab_type: string, metadata?: Record<string, any>) {
    sendTrackingEvent({
        action,
        tab_type,
        metadata: metadata ?? {},
    })
}

export function trackBlueprintSelection(blueprintId: string) {
    makeEvent("blueprint_selection", "blueprint", {blueprint_name: blueprintId})
}

export function trackPluginDocumentationView(pluginClass: string) {
    makeEvent("plugin_doc", "documentation", {documentation_page: pluginClass})
}

export function trackFileOpen(fileName: string) {
    makeEvent("files_open", "files_browser", {file_name: fileName})
}

export type AuthoringAction = "task_added" | "task_edited" | "task_deleted" | "task_moved" | "task_duplicated"
export type AuthoringSurface = "no_code" | "topology"

export interface AuthoringActionMetadata {
    task_type?: string
    position?: "before" | "after"
}

/** Distinguishes "opened this surface" (trackTabOpen) from "actually built a flow with it". */
export function trackAuthoringAction(
    action: AuthoringAction,
    surface: AuthoringSurface,
    metadata?: AuthoringActionMetadata,
) {
    makeEvent(action, surface, metadata ?? {})
}
