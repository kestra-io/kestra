package io.kestra.webserver.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BulkErrorResponse {
    String message;
    Object invalids;
}
