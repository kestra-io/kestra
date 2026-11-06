import semver from "semver"

export type PluginElement = {
    cls: string;
    deprecated?: boolean;
    title?: string;
    description?: string;
    aliases?: string[];
};

export type PluginAuthor = {
    name: string;
    avatarUrl?: string;
};

export type PluginIconMap = Record<string, {flowable: boolean; monochrome: boolean; hasIcon: boolean; iconUrl?: string; hash?: string}>;

export type Plugin = {
    name: string;
    title: string;
    group: string;
    longDescription?: string;
    description?: string;
    subGroup?: string;
    version?: string;
    tooltipContent?: string;
    categories?: string[];
    controllers?: string[];
    storages?: string[];
    aliases?: string[];
    guides?: string[];
    manifest?: Record<string, string>;
    // PluginList.vue:113 destructured elements as an array with property of `deprecated` and `cls`
    // since string[] is one of the choices, we need to allow unknown type here
    [pluginElement: string]: PluginElement[] | string | string[] | Record<string, string> | undefined | unknown;
};

// Element group names reach $t() as a bare variable in PluginUnified.vue, PluginCatalog.vue and EE's Plugin.vue.
// i18n-keys: tasks, triggers, taskRunners, apps, appBlocks, charts, dataFilters, logExporters, additionalPlugins
export function isEntryAPluginElementPredicate(key: string, value: unknown): value is PluginElement[] {
    return Array.isArray(value) &&
        !["categories", "controllers", "storages", "aliases", "guides"].includes(key) &&
        (value.length === 0 ||
        value[0]?.cls !== undefined)
}

export function pluginElementType(plugin: Plugin | null | undefined, cls: string | null | undefined): string | undefined {
    if (!plugin || !cls) return undefined
    return Object.entries(plugin)
        .find(([key, value]) => isEntryAPluginElementPredicate(key, value) && value.some(el => el?.cls === cls || el?.aliases?.includes(cls)))?.[0]
}

export function countUniquePluginElements(plugins: Plugin[]): number {
    const classes = new Set<string>()
    for (const plugin of plugins) {
        for (const [key, value] of Object.entries(plugin)) {
            if (isEntryAPluginElementPredicate(key, value)) {
                value.forEach(({cls}) => classes.add(cls))
            }
        }
    }
    return classes.size
}

export function extractPluginElements(plugin: Plugin): Record<string, string[]> {
    return Object.fromEntries(
        Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .map(([key, value]) => [key.replace(/[A-Z]/g, match => ` ${match}`), (value as PluginElement[]).filter(({deprecated}) => !deprecated).map(({cls}) => cls)]),
    )
}

export function isPluginMatched(plugin: Plugin, search: string): boolean {
    if (!search) return true
    const q = search.toLowerCase()
    return [
        plugin.title,
        plugin.name,
        plugin.group,
        plugin.manifest?.["X-Kestra-Title"],
    ].some(field => field?.toLowerCase().includes(q)) ||
        pluginElementClasses(plugin).some(cls => cls.toLowerCase().includes(q))
}

export function pluginElementClasses(plugin: Plugin): string[] {
    return Object.values(extractPluginElements(plugin)).flat()
}

export function compareVersionsDesc(a: string, b: string): number {
    const va = semver.coerce(a)?.version
    const vb = semver.coerce(b)?.version
    return va && vb ? semver.rcompare(va, vb) : b.localeCompare(a)
}

export function latestVersionEntries(plugins: Plugin[]): Plugin[] {
    const latest = new Map<string, Plugin>()
    for (const plugin of plugins) {
        const key = `${plugin.name}:${plugin.subGroup ?? ""}`
        const current = latest.get(key)
        if (!current || compareVersionsDesc(plugin.version ?? "", current.version ?? "") < 0) {
            latest.set(key, plugin)
        }
    }
    return [...latest.values()]
}

export const isEnterpriseEditionPlugin = (classOrGroup?: string | null): boolean =>
    Boolean(classOrGroup?.includes(".ee."))

export const getPluginReleaseUrl = (pluginClass?: string): string | null => {
    const [, , groupId, pluginType] = pluginClass?.split(".") ?? []

    if (!pluginType || pluginType === "ee" || pluginType === "secret") {
        return null
    }

    if (pluginType === "core") {
        return "https://github.com/kestra-io/kestra/releases"
    }

    const repoPrefix = groupId === "storage" ? "storage" : "plugin"
    return `https://github.com/kestra-io/${repoPrefix}-${pluginType}/releases`
}
