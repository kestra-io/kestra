package io.kestra.core.test;

import io.kestra.core.test.flow.UnitTest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.util.List;

@Getter
@Builder
public class TestSuite {
    @NotNull
    private String id;

    private String description;

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @With
    private String source;

    @NotNull
    @NotEmpty
    private List<UnitTest> testCases;
}
