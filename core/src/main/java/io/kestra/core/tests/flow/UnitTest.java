package io.kestra.core.tests.flow;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.PROPERTY)
public class UnitTest implements io.kestra.core.models.Plugin {
    @NotNull
    private String id;

    @NotNull
    private String type;

    @Builder.Default
    private boolean disabled = false;

    private String description;

    @Valid
    private Fixtures fixtures;

    @NotNull
    @Valid
    private List<Assertion> assertions;
}
