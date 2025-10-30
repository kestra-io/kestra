package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@AllArgsConstructor
@Builder
public class ConcurrencyLimit implements HasUID {
    @NotNull
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @With
    Integer running;

    @Override
    public String uid() {
        return IdUtils.fromPartsAndSeparator('|', this.tenantId, this.namespace, this.flowId);
    }
}
