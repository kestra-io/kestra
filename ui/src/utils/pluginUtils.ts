export const getPluginReleaseUrl = (pluginClass?: string): string | null => {
    const [, , groupId, pluginType] = pluginClass?.split(".") ?? [];
    
    return !pluginType ? null 
        : pluginType === "core" ? "https://github.com/kestra-io/kestra/releases"
        : pluginType === "ee" ? null
        : pluginType === "secret" ? null
        : `https://github.com/kestra-io/${groupId === "storage" ? "storage" : "plugin"}-${pluginType}/releases`;
};