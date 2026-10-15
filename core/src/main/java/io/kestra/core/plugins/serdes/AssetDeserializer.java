package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.databind.JsonDeserializer;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.Custom;
import io.kestra.core.models.assets.External;

/**
 * Specific {@link JsonDeserializer} for deserializing {@link Asset}.
 */
public final class AssetDeserializer extends PluginDeserializer<Asset> {
    @Override
    protected Class<? extends Plugin> fallbackClass() {
        return Custom.class;
    }

    /**
     * An asset declared by its id alone is external, as for the assets referenced in {@code assets.inputs}.
     * That placeholder type never overwrites a stored asset's real type, see {@link Asset#toUpdated(Asset, boolean)}.
     */
    @Override
    protected Class<? extends Plugin> defaultClass() {
        return External.class;
    }
}
