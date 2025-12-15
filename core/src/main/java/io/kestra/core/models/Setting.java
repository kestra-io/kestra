package io.kestra.core.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Setting {
    public static final String INSTANCE_UUID = "instance.uuid";
    public static final String INSTANCE_VERSION = "instance.version";
    public static final String INSTANCE_EDITION = "instance.edition";

    @NotNull
    private String key;

    @NotNull
    private Object value;
}
