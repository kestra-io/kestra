package io.kestra.webserver.models.api.secret;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApiSecretListResponse<META extends ApiSecretMeta>(boolean readOnly, List<META> results, long total) {
    public ApiSecretListResponse(
        @NotNull
        @Parameter(
            name = "readOnly",
            description = "Specifies whether secrets are read-only",
            required = true)
        boolean readOnly,
        @NotNull
        @Parameter(
            name = "results",
            description = "List of secrets",
            required = true)
        List<META> results,
        @Parameter(
            name = "total",
            description = "Total number of available secrets",
            required = true)
        long total
    ) {
        this.readOnly = readOnly;
        this.results = results;
        this.total = total;
    }
}
