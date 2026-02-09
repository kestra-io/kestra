package io.kestra.core.runners;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;

import java.util.List;

public record AssetEmit(List<AssetIdentifier> inputs, List<Asset> outputs) {
}
