package org.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
@ToString
abstract public class AbstractChildDocumentation<T extends AbstractChildDocumentation<T>> {
    private final Class<?> parent;

    private final String name;

    private final String type;

    private final List<String> values;

    protected AbstractChildDocumentation(Class<?> parent, String name, String type, Object[] values, List<T> childs) {
        this.parent = parent;
        this.name = name;
        this.type = type;
        this.values = values == null ? null : Arrays.stream(values).map(Object::toString).collect(Collectors.toList());
        this.childs = childs;
    }

    @JsonIgnore
    private final List<T> childs;
}
