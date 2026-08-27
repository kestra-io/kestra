import {computed} from "vue"

import {usePluginsStore} from "../stores/plugins"

const PLUGIN_PACKAGE_PREFIX = "io.kestra.plugin."

// Conditions are the one flow element the plugins endpoint does not return, yet
// they appear in a blueprint's includedTasks. They are validated by the flow
// schema (not the plugin list), so they must never be treated as "missing".
const CONDITION_CLASS_PATTERN = /\.conditions?\./

const CONCRETE_CLASS_PATTERN = /\.[A-Z][^.]*$/

/**
 * Unique concrete task class names of a blueprint. includedTasks also carries
 * pluginDefaults types, which may be whole plugin groups (lowercase last
 * segment, e.g. io.kestra.plugin.jdbc.postgresql); those are excluded.
 */
export function blueprintTaskTypes(includedTasks?: string[]): string[] {
    return [...new Set(includedTasks ?? [])].filter(type => CONCRETE_CLASS_PATTERN.test(type))
}

/** Derives a short, user-facing plugin name from a task class name. */
function pluginNameOf(taskType: string): string {
    if (taskType.startsWith(PLUGIN_PACKAGE_PREFIX)) {
        return taskType.slice(PLUGIN_PACKAGE_PREFIX.length).split(".")[0]
    }
    const parts = taskType.split(".")
    return parts.length > 1 ? parts[parts.length - 2] : taskType
}

/**
 * Detects whether a blueprint relies on a task type that cannot be resolved on
 * the current instance. A blueprint exposes its task class names through
 * `includedTasks`; the installed types come from the plugins endpoint
 * (`GET /api/v1/plugins`, exposed by the plugins store as `installedPluginTypes`).
 *
 * Matching is done on the exact class name — the same granularity the editor
 * uses to validate, and the granularity that matters since sibling plugins can
 * share a package prefix (e.g. `scripts.python` vs `scripts.shell` are distinct
 * artifacts). Condition classes are excluded because the endpoint does not list
 * them.
 *
 * A type can be unresolvable for two different reasons, which the UI must not
 * conflate: its plugin is absent (installing it fixes the blueprint), or the
 * plugin is installed but no longer ships that type because it was renamed or
 * removed (no install helps).
 */
export function useBlueprintPlugins() {
    const pluginsStore = usePluginsStore()

    const installedTypes = computed(() => new Set(pluginsStore.installedPluginTypes ?? []))

    const installedPluginNames = computed(() => new Set([...installedTypes.value].map(pluginNameOf)))

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

    /**
     * Task types referenced by the blueprint whose plugin is not installed.
     * Returns nothing until the installed set is known, so blueprints are never
     * disabled on the basis of missing (not-yet-loaded) data.
     */
    const missingTaskTypes = (includedTasks?: string[]): string[] => {
        if (installedTypes.value.size === 0) return []
        return blueprintTaskTypes(includedTasks).filter(
            type => !CONDITION_CLASS_PATTERN.test(type) && !installedTypes.value.has(type),
        )
    }

    /**
     * Unique, sorted names of the plugins that are absent altogether, so that
     * installing them would make the blueprint usable. A type that an installed
     * plugin no longer ships (renamed or removed) contributes nothing here.
     */
    const uninstalledPluginNames = (includedTasks?: string[]): string[] =>
        [...new Set(
            missingTaskTypes(includedTasks)
                .map(pluginNameOf)
                .filter(name => !installedPluginNames.value.has(name)),
        )].sort()

    const hasMissingPlugins = (includedTasks?: string[]): boolean =>
        missingTaskTypes(includedTasks).length > 0

    return {
        installedTypes,
        ensureInstalledPluginsLoaded,
        missingTaskTypes,
        uninstalledPluginNames,
        hasMissingPlugins,
    }
}
