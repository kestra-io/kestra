package io.kestra.core.models.validations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

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
    private List<String> deprecationPaths;

    @JsonIgnore
    public String getIdentity(){
        return flow != null & namespace != null ? getFlowId() : flow != null ? flow : String.valueOf(index);
    }

    @JsonIgnore
    public String getIdentity(Path directory) throws IOException {
        return flow != null & namespace != null ? getFlowId() : flow != null ? flow : String.valueOf(Files.walk(directory).collect(Collectors.toList()).get(index));
    }

    @JsonIgnore
    public String getFlowId(){
        return namespace+"."+flow;
    }
}
