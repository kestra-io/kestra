package io.kestra.webserver.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BulkResponse {
    @Schema(description = "The number of items successfully processed")
    Integer count;
}
