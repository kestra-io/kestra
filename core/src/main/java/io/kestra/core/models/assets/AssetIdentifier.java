package io.kestra.core.models.assets;

import java.util.Objects;

import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;

/**
 * @param fullId the id as given, when {@link Asset#normalizeId(String)} changed it
 */
public record AssetIdentifier(@Hidden String tenantId, @Hidden String namespace, @NotBlank String id, String type, @Hidden String fullId) {

    public AssetIdentifier {
        String normalized = Asset.normalizeId(id);
        if (fullId == null && !Objects.equals(id, normalized)) {
            fullId = id;
        }
        id = normalized;
    }

    public AssetIdentifier(String tenantId, String namespace, String id, String type) {
        this(tenantId, namespace, id, type, null);
    }

    public AssetIdentifier withTenantId(String tenantId) {
        return new AssetIdentifier(tenantId, this.namespace, this.id, this.type, this.fullId);
    }

    public AssetIdentifier withNamespace(String namespace) {
        return new AssetIdentifier(this.tenantId, namespace, this.id, this.type, this.fullId);
    }

    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    public static AssetIdentifier of(Asset asset) {
        return new AssetIdentifier(asset.getTenantId(), asset.getNamespace(), asset.getId(), asset.getType());
    }
}
