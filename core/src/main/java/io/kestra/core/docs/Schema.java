package io.kestra.core.docs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@io.swagger.v3.oas.annotations.media.Schema(
    name = "PluginSchema"
)
public class Schema {
    private Map<String, Object> properties;
    private Map<String, Object> outputs;
    private Map<String, Object> definitions;
}
