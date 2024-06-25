package io.kestra.webserver.responses;

import io.kestra.core.models.validations.ManualConstraintViolation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@SuperBuilder
@Getter
@NoArgsConstructor
public class BulkErrorResponse {
    String message;
    Object invalids;
}
