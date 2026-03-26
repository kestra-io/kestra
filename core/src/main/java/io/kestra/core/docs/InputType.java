package io.kestra.core.docs;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class InputType {
    String type;
    String cls;
}
