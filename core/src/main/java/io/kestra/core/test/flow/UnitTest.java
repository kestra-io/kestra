package io.kestra.core.test.flow;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.flows.State;
import io.kestra.core.validations.DurationMax;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.time.DurationMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
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
    private List<@Valid Assertion> assertions;

    @Builder.Default
    private State.Type expectedState = State.Type.SUCCESS;

    @Builder.Default
    @DurationMax
    @DurationMin(millis = 1, message = "must be a positive duration")
    @Schema(description = "The maximum time the tested execution may run. When it is reached, the execution is killed and the test case ends in ERROR.")
    private Duration maxDuration = Duration.ofMinutes(5);

    @AssertTrue(message = "The expected state should be a terminal state.")
    @JsonIgnore
    public boolean isInTerminalState() {
        return expectedState == null || expectedState.isTerminated();
    }
}
