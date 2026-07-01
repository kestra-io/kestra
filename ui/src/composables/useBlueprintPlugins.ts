import {computed} from "vue"

import {usePluginsStore} from "../stores/plugins"

const PLUGIN_PACKAGE_PREFIX = "io.kestra.plugin."

// Conditions are the one flow element the plugins endpoint does not return, yet
// they appear in a blueprint's includedTasks. They are validated by the flow
// schema (not the plugin list), so they must never be treated as "missing".
const CONDITION_CLASS_PATTERN = /\.conditions?\./

/** Derives a short, user-facing plugin name from a task class name. */
function pluginNameOf(taskType: string): string {
    if (taskType.startsWith(PLUGIN_PACKAGE_PREFIX)) {
        return taskType.slice(PLUGIN_PACKAGE_PREFIX.length).split(".")[0]
    }
    const parts = taskType.split(".")
    return parts.length > 1 ? parts[parts.length - 2] : taskType
}

/**
 * Detects whether a blueprint relies on a task whose plugin is not installed on
 * the current instance. A blueprint exposes its task class names through
 * `includedTasks`; the installed types come from the plugins endpoint
 * (`GET /api/v1/plugins`, exposed by the plugins store as `installedPluginTypes`).
 *
 * Matching is done on the exact class name — the same granularity the editor
 * uses to validate, and the granularity that matters since sibling plugins can
 * share a package prefix (e.g. `scripts.python` vs `scripts.shell` are distinct
 * artifacts). Condition classes are excluded because the endpoint does not list
 * them.
 */
export function useBlueprintPlugins() {
    const pluginsStore = usePluginsStore()

    const installedTypes = computed(() => new Set(pluginsStore.installedPluginTypes ?? []))

    /**
     * Loads the installed plugin types once. Failures are swallowed: if the list
     * can't be fetched the installed set stays empty and nothing is disabled.
     */
    const ensureInstalledPluginsLoaded = async (): Promise<void> => {
        try {
            await pluginsStore.loadInstalledPluginTypes()
        } catch {
            // Leave the installed set empty; nothing gets flagged as missing.
        }
    }

    /** Exposed for display (e.g. building the missing-plugin message). */
    const pluginName = pluginNameOf

    /**
     * Task types referenced by the blueprint whose plugin is not installed.
     * Returns nothing until the installed set is known, so blueprints are never
     * disabled on the basis of missing (not-yet-loaded) data.
     */
    const missingTaskTypes = (includedTasks?: string[]): string[] => {
        if (installedTypes.value.size === 0) return []
        return [...new Set(includedTasks ?? [])].filter(
            type => !CONDITION_CLASS_PATTERN.test(type) && !installedTypes.value.has(type),
        )
    }

    /** Unique, sorted plugin names that need to be installed for the blueprint. */
    const missingPluginNames = (includedTasks?: string[]): string[] =>
        [...new Set(missingTaskTypes(includedTasks).map(pluginNameOf))].sort()

    const hasMissingPlugins = (includedTasks?: string[]): boolean =>
        missingTaskTypes(includedTasks).length > 0

    return {
        installedTypes,
        ensureInstalledPluginsLoaded,
        pluginName,
        missingTaskTypes,
        missingPluginNames,
        hasMissingPlugins,
    }
}
