package io.kestra.core.models.assets;

import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;

public record AssetIdentifier(@Hidden String tenantId, @Hidden String namespace, @NotBlank String id, String type) {

    public AssetIdentifier withTenantId(String tenantId) {
        return new AssetIdentifier(tenantId, this.namespace, this.id, this.type);
    }

    public AssetIdentifier withNamespace(String namespace) {
        return new AssetIdentifier(this.tenantId, namespace, this.id, this.type);
    }

    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    public static AssetIdentifier of(Asset asset) {
        return new AssetIdentifier(asset.getTenantId(), asset.getNamespace(), asset.getId(), asset.getType());
    }
}
