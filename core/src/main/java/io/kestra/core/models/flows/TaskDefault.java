package io.kestra.core.models.flows;

import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import javax.annotation.Nullable;

@Getter
@Builder
@AllArgsConstructor
public class TaskDefault {
    private final String type;

    @Builder.Default
    private final boolean forced = false;

    @MapFormat(transformation = MapFormat.MapTransformation.NESTED, keyFormat = StringConvention.CAMEL_CASE)
    private final Map<String, Object> values;
}

