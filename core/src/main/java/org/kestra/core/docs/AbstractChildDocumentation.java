package org.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
abstract public class AbstractChildDocumentation<T extends AbstractChildDocumentation<T>> {
    @JsonIgnore
    private Class<?> parent;
    private String name;
    private String type;
    private List<String> values;

    protected AbstractChildDocumentation(Class<?> parent, String name, String type, Object[] values, List<T> childs) {
        this.parent = parent;
        this.name = name;
        this.type = type;
        this.values = values == null ? null : Arrays.stream(values).map(Object::toString).collect(Collectors.toList());
        this.childs = childs;
    }

    @JsonIgnore
    private List<T> childs;
}
