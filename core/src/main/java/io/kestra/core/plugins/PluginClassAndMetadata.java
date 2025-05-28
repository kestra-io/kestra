package io.kestra.core.plugins;

public record PluginClassAndMetadata<T>(
    Class<? extends T> type,
    Class<T> baseClass,
    String group,
    String license,
    String title,
    String icon,
    String alias
) {
    public static <T> PluginClassAndMetadata<T> create(RegisteredPlugin registered,
                                                       Class<? extends T> pluginClass,
                                                       Class<T> pluginBaseClass,
                                                       String alias) {
        return new PluginClassAndMetadata<>(
            pluginClass,
            pluginBaseClass,
            registered.group(),
            registered.license(),
            registered.title(),
            registered.icon(pluginClass),
            alias
        );
    }
}
