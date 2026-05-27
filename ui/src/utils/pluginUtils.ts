export type PluginElement = {
    cls: string;
    deprecated?: boolean;
    title?: string;
    description?: string;
};

export type PluginAuthor = {
    name: string;
    avatarUrl?: string;
};

export type PluginIconMap = Record<string, {icon: string; flowable: boolean}>;

export type Plugin = {
    name: string;
    title: string;
    group: string;
    longDescription?: string;
    description?: string;
    subGroup?: string;
    tooltipContent?: string;
    categories?: string[];
    controllers?: string[];
    storages?: string[];
    aliases?: string[];
    guides?: string[];
    manifest?: Record<string, any>;
    [pluginElement: string]: PluginElement[] | string | string[] | Record<string, any> | undefined;
};

export function isEntryAPluginElementPredicate(key: string, value: any): value is PluginElement[] {
    return Array.isArray(value) &&
        !["categories", "controllers", "storages", "aliases", "guides"].includes(key) &&
        ((value as any[]).length === 0 ||
        value[0]?.cls !== undefined)
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
        Object.values(extractPluginElements(plugin)).flat().some(cls => cls.toLowerCase().includes(q))
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
