package io.kestra.core.docs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Schema {
    private Map<String, Object> properties;
    private Map<String, Object> outputs;
    private Map<String, Object> definitions;
}
