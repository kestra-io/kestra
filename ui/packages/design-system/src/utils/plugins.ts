export type PluginElement = {
    cls: string;
    deprecated?: boolean;
}

export type PluginMetadata = {
    group: string;
    artifactGroupId: string;
    artifactId: string;
    name: string;
    title: string;
    description?: string;
    videos?: string[];
    createdBy?: string;
    managedBy?: string;
    version?: string;
    icon?: string;
}

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
}

export function isEntryAPluginElementPredicate(key: string, value: any): value is PluginElement[] {
    return Array.isArray(value) &&
        !["categories", "controllers", "storages", "aliases", "guides"].includes(key) &&
        ((value as any[]).length === 0 ||
        value[0]?.cls !== undefined)
}

export function subGroupName(subGroupWrapper: {title?: string}): string {
    const title = subGroupWrapper.title ?? ""
    const result = title.replace(/\.([a-zA-Z])/g, (_, capture) => ` ${capture.toUpperCase()}`)
    return result.charAt(0).toUpperCase() + result.slice(1)
}

export function extractPluginElements(plugin: Plugin): Record<string, string[]> {
    return Object.fromEntries(
        Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .map(([key, value]) => [
                key.replace(/[A-Z]/g, match => ` ${match}`),
                (value as PluginElement[]).filter(({deprecated}) => !deprecated).map(({cls}) => cls),
            ]),
    )
}

export function slugifyPlugin(text: string): string {
    return text.toLowerCase().replace(/\s+/g, "-")
}
