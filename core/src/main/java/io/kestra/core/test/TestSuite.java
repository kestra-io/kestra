package io.kestra.core.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasSource;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TestSuiteValidation;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
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
    @Valid
    private List<UnitTest> testCases;

    @Builder.Default
    private boolean deleted = Boolean.FALSE;

    @Builder.Default
    private boolean disabled = Boolean.FALSE;

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
            newTestSuite.isDisabled()
            );
    }

    public TestSuite delete() {
        return this.toBuilder().deleted(true).build();
    }

    public TestSuite disable() {
        var disabled = true;
        return this.toBuilder()
            .disabled(disabled)
            .source(toggleDisabledInYamlSource(this.source, disabled))
            .build();
    }

    public TestSuite enable() {
        var disabled = false;
        return this.toBuilder()
            .disabled(disabled)
            .source(toggleDisabledInYamlSource(this.source, disabled))
            .build();
    }

    @Override
    public String source() {
        return this.getSource();
    }

    protected static String toggleDisabledInYamlSource(String yamlSource, boolean disabled) {
        String regex = disabled ? "^disabled\\s*:\\s*false\\s*" : "^disabled\\s*:\\s*true\\s*";

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.MULTILINE);
        if (p.matcher(yamlSource).find()) {
            return p.matcher(yamlSource).replaceAll(String.format("disabled: %s\n", disabled));
        }

        return yamlSource + String.format("\ndisabled: %s", disabled);
    }
}
