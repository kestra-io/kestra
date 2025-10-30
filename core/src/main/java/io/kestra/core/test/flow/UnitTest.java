package io.kestra.core.test.flow;

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
public class UnitTest {
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
