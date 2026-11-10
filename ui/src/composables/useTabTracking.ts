import {usePluginsStore} from "../stores/plugins"
import {useBlueprintsStore} from "../stores/blueprints"
import {getTabType, makeEvent, type TrackedTab} from "../utils/tabTracking"

export function useTabTracking() {
    const pluginsStore = usePluginsStore()
    const blueprintsStore = useBlueprintsStore()

    function getTabMetadata(tab: TrackedTab): Record<string, unknown> {
        const metadata: Record<string, unknown> = {}
        const value = tab.uid

        if (value === "doc" && pluginsStore.editorPlugin?.cls) {
            metadata.documentation_page = pluginsStore.editorPlugin.cls
        }

        if (value === "blueprints" && blueprintsStore.blueprint?.id) {
            metadata.blueprint_name = blueprintsStore.blueprint.id
        }

        if (value.startsWith("nocode-")) {
            try {
                const tabData = JSON.parse(value.substring(12))
                if (tabData.taskType) {
                    metadata.task_type = tabData.taskType
                }
            } catch {
                // Ignore parsing errors
            }
        }

        return metadata
    }

    function trackTabOpen(tab: TrackedTab) {
        makeEvent("open", getTabType(tab), getTabMetadata(tab))
    }

    function trackTabClose(tab: TrackedTab) {
        makeEvent("close", getTabType(tab), getTabMetadata(tab))
    }

    return {trackTabOpen, trackTabClose}
}
