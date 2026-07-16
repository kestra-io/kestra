package io.kestra.core.plugins.serdes;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.Custom;

import tools.jackson.databind.ValueDeserializer;

/**
 * Specific {@link ValueDeserializer} for deserializing {@link Asset}.
 */
public final class AssetDeserializer extends PluginDeserializer<Asset> {
    @Override
    protected Class<? extends Plugin> fallbackClass() {
        return Custom.class;
    }
}
