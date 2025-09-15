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