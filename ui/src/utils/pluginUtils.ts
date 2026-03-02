import {extractPluginElements, type Plugin} from "@kestra-io/ui-libs";

export function isPluginMatched(plugin: Plugin, search: string): boolean {
    if (!search) return true;
    const q = search.toLowerCase();
    return [
        plugin.title,
        plugin.name,
        plugin.group,
        plugin.manifest?.["X-Kestra-Title"],
    ].some(field => field?.toLowerCase().includes(q)) ||
        Object.values(extractPluginElements(plugin)).flat().some(cls => cls.toLowerCase().includes(q));
}

export const getPluginReleaseUrl = (pluginClass?: string): string | null => {
    const [, , groupId, pluginType] = pluginClass?.split(".") ?? [];

    if (!pluginType || pluginType === "ee" || pluginType === "secret") {
        return null;
    }

    if (pluginType === "core") {
        return "https://github.com/kestra-io/kestra/releases";
    }

    const repoPrefix = groupId === "storage" ? "storage" : "plugin";
    return `https://github.com/kestra-io/${repoPrefix}-${pluginType}/releases`;
};

