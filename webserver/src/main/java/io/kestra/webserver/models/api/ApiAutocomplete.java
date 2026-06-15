package io.kestra.webserver.models.api;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ApiAutocomplete {
    @Parameter(description = "A string filter")
    @Nullable
    String q;
    @Parameter(description = "The ids that must be present on results")
    @Nullable
    List<String> ids;
    @Parameter(description = "Return only existing namespace")
    boolean existingOnly;
}
