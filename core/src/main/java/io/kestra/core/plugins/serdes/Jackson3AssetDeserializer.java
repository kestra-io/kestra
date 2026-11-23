package io.kestra.core.plugins.serdes;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.Custom;

/**
 * Jackson 3 counterpart of {@link AssetDeserializer}, for deserializing {@link Asset} on the HTTP boundary.
 */
public final class Jackson3AssetDeserializer extends Jackson3PluginDeserializer<Asset> {
    @Override
    protected Class<? extends Plugin> fallbackClass() {
        return Custom.class;
    }
}
