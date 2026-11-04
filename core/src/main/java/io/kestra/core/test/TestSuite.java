package io.kestra.core.test;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.HasSource;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.annotations.ServerOwned;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;
import io.kestra.core.validations.TestSuiteValidation;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@TestSuiteValidation
public class TestSuite implements HasUID, TenantInterface, SoftDeletable<TestSuite>, HasSource {

    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    private String id;

    @Hidden
    @TenantId
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

    @ServerOwned
    private String source;

    @NotNull
    @NotEmpty
    private List<@Valid UnitTest> testCases;

    @Builder.Default
    @ServerOwned
    private boolean deleted = Boolean.FALSE;

    @Builder.Default
    private boolean disabled = Boolean.FALSE;

    @Min(1)
    @ServerOwned
    @Schema(description = "The revision of the test suite, bumped on every save.", accessMode = Schema.AccessMode.READ_ONLY)
    @Builder.Default
    private Integer revision = 1;

    @Hidden
    @ServerOwned
    @Builder.Default
    private boolean last = true;

    @Nullable
    @ServerOwned
    @Schema(description = "When this revision was saved.", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updated;

    @Nullable
    @ServerOwned
    @Schema(description = "The username of the user or service account that saved this revision.", accessMode = Schema.AccessMode.READ_ONLY)
    private String updatedBy;

    /** The current revision keeps the key it had before revisions existed, so stores need no key migration. */
    @Override
    @JsonIgnore
    public String uid() {
        return last ? uidWithoutRevision() : IdUtils.fromParts(tenantId, namespace, id, String.valueOf(revision));
    }

    @JsonIgnore
    public String uidWithoutRevision() {
        return IdUtils.fromParts(tenantId, namespace, id);
    }

    public TestSuite update(final String newSource, final TestSuite newTestSuite) {
        return newTestSuite.toBuilder()
            .tenantId(this.tenantId)
            .source(newSource)
            .build();
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

    @Override
    public TestSuite toDeleted() {
        return toBuilder().deleted(true).build();
    }
}
