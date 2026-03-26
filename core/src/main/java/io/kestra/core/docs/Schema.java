package io.kestra.core.docs;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
