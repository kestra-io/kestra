package io.kestra.webserver.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@SuperBuilder
@Getter
@NoArgsConstructor
@Slf4j
public class BulkErrorResponse {
    String message;
    Set<ManualConstraintViolation<String>> invalids;

    public String getMessage(){
        var serializedViolations = "could not be serialized";
        try {
            serializedViolations = JacksonMapper.ofJson().writeValueAsString(invalids);
        } catch (JsonProcessingException e) {
            log.warn("could not serialize invalids field for BulkErrorResponse", e);
        }
        return this.message + ", violations: " + serializedViolations;
    }
}
