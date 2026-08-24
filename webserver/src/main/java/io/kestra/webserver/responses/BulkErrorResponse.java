package io.kestra.webserver.responses;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BulkErrorResponse {
    @Schema(description = "The error message")
    String message;
    @Schema(description = "The list of items that failed validation")
    Set<?> invalids;
}
