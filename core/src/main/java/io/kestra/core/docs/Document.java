package io.kestra.core.docs;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class Document {
    private final String path;
    private final String body;
    private final String icon;
    private final Schema schema;
}
