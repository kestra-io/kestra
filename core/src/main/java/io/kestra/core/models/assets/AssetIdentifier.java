package io.kestra.core.models.assets;

import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.Hidden;

public record AssetIdentifier(@Hidden String tenantId, @Hidden String namespace, String id){

    public AssetIdentifier withTenantId(String tenantId) {
        return new AssetIdentifier(tenantId, this.namespace, this.id);
    }

    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    public static AssetIdentifier of(Asset asset) {
        return new AssetIdentifier(asset.getTenantId(), asset.getNamespace(), asset.getId());
    }
}
