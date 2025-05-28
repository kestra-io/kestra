package io.kestra.core.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasSource;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TestSuiteValidation;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
@TestSuiteValidation
public class TestSuite implements HasUID, TenantInterface, DeletedInterface, HasSource {

    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    private String id;

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    private String description;

    @NotNull
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    private String namespace;

    @NotNull
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*")
    @Size(min = 1, max = 100)
    private String flowId;

    private String source;

    @NotNull
    @NotEmpty
    private List<UnitTest> testCases;

    @JsonProperty("deleted")
    boolean isDeleted = Boolean.FALSE;

    @Builder.Default
    private Boolean disabled = Boolean.FALSE;

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(
            tenantId,
            namespace,
            id
        );
    }

    public TestSuite update(final String newSource, final TestSuite newTestSuite) {
        return new TestSuite(
            newTestSuite.getId(),
            this.tenantId,
            newTestSuite.getDescription(),
            newTestSuite.getNamespace(),
            newTestSuite.getFlowId(),
            newSource,
            newTestSuite.getTestCases(),
            newTestSuite.isDeleted(),
            newTestSuite.getDisabled()
            );
    }

    public TestSuite delete() {
        return this.toBuilder().isDeleted(true).build();
    }

    @Override
    public String source() {
        return this.getSource();
    }
}
