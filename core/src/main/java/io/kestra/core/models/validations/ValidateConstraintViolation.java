package io.kestra.core.models.validations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@Slf4j
public class ValidateConstraintViolation {
    private String flow;

    private String namespace;

    @NotNull
    private int index;

    private String constraints;
    private boolean outdated;
    private List<String> deprecationPaths;

    @JsonIgnore
    public String getIdentity(){
        return flow != null && namespace != null ? getFlowId() : flow != null ? flow : String.valueOf(index);
    }

    @JsonIgnore
    public String getIdentity(Path directory) throws IOException {
        return flow != null && namespace != null ? getFlowId() : flow != null ? flow : getPath(directory);
    }

    private String getPath(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            return String.valueOf(files.toList().get(index));
        }
    }

    @JsonIgnore
    public String getFlowId(){
        return namespace+"."+flow;
    }
}
