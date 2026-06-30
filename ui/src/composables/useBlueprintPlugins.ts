import {computed} from "vue"

import {usePluginsStore} from "../stores/plugins"

const PLUGIN_PACKAGE_PREFIX = "io.kestra.plugin."

/**
 * Detects whether a blueprint relies on task types whose plugin is not installed
 * on the current instance. A blueprint exposes its task class names through
 * `includedTasks`; the installed task types are the ones the plugins endpoint
 * (`GET /api/v1/plugins`) returns, exposed by the plugins store as `allTypes`.
 *
 * When a required task type is missing the flow editor errors out, so we use the
 * same source of truth to disable the blueprint upfront and tell the user which
 * plugin(s) they need to install.
 */
export function useBlueprintPlugins() {
    const pluginsStore = usePluginsStore()

    const installedTaskTypes = computed(() => new Set(pluginsStore.allTypes))

    /**
     * Loads the installed plugin task types once. Failures are swallowed: if the
     * list can't be fetched we simply don't disable any blueprint.
     */
    const ensureInstalledPluginsLoaded = async (): Promise<void> => {
        if (pluginsStore.allTypes.length > 0) return
        try {
            await pluginsStore.list()
        } catch {
            // Leave the installed set empty; nothing gets flagged as missing.
        }
    }

    /** Derives a short, user-facing plugin name from a task class name. */
    const pluginName = (taskType: string): string => {
        if (taskType.startsWith(PLUGIN_PACKAGE_PREFIX)) {
            return taskType.slice(PLUGIN_PACKAGE_PREFIX.length).split(".")[0]
        }
        const parts = taskType.split(".")
        return parts.length > 1 ? parts[parts.length - 2] : taskType
    }

    /** Task types referenced by the blueprint that are not installed. */
    const missingTaskTypes = (includedTasks?: string[]): string[] =>
        [...new Set(includedTasks ?? [])].filter(type => !installedTaskTypes.value.has(type))

    /** Unique, sorted plugin names that need to be installed for the blueprint. */
    const missingPluginNames = (includedTasks?: string[]): string[] =>
        [...new Set(missingTaskTypes(includedTasks).map(pluginName))].sort()

    const hasMissingPlugins = (includedTasks?: string[]): boolean =>
        missingTaskTypes(includedTasks).length > 0

    return {
        installedTaskTypes,
        ensureInstalledPluginsLoaded,
        pluginName,
        missingTaskTypes,
        missingPluginNames,
        hasMissingPlugins,
    }
}
