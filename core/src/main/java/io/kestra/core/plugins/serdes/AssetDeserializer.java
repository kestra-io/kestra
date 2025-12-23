package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.databind.JsonDeserializer;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.Custom;

/**
 * Specific {@link JsonDeserializer} for deserializing {@link Asset}.
 */
public final class AssetDeserializer extends PluginDeserializer<Asset> {
    @Override
    protected Class<? extends Plugin> fallbackClass() {
        return Custom.class;
    }
}
