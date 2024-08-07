package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawTask extends Task {

    @JsonAnySetter
    Map<String, Object> properties;

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }
}
