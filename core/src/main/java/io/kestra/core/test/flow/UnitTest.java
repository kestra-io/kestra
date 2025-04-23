package io.kestra.core.test.flow;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UnitTest {
    @NotNull
    private String id;

    @NotNull
    private String type;

    @Builder.Default
    private boolean disabled = false;

    private String description;

    private Fixtures fixtures;

    @NotNull
    private List<Assertion> assertions;
}
