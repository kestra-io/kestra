package io.kestra.core.runners;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.ReusableInputsInput;
import io.kestra.core.models.flows.input.StringInput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledReusableInputsExpanderTest {

    /** Reusable inputs are an Enterprise Edition feature: the open-source default errors when one is referenced. */
    @Test
    void erroringWhenReferencedInOpenSource() {
        DisabledReusableInputsExpander expander = new DisabledReusableInputsExpander();

        ReusableInputsInput reference = ReusableInputsInput.builder()
            .id("myBlock").type(Type.REUSABLE_INPUTS).ref("infra_request").build();

        assertThatThrownBy(() -> expander.resolve("main", "company", reference))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Enterprise Edition");
    }

    /**
     * {@code expand()} only touches the store-backed {@code resolve()} for actual references, so a flow with no
     * reusable inputs returns its inputs unchanged even on open-source (never hitting the erroring default).
     */
    @Test
    void expandIsANoOpWhenNoReferencePresent() {
        DisabledReusableInputsExpander expander = new DisabledReusableInputsExpander();

        List<Input<?>> inputs = List.of(StringInput.builder().id("a").type(Type.STRING).build());

        assertThat(expander.expand("main", "company", inputs)).isSameAs(inputs);
    }

    /** A reference present in open-source still errors through {@code expand()} (it delegates to {@code resolve()}). */
    @Test
    void expandErrorsWhenAReferenceIsPresentInOpenSource() {
        DisabledReusableInputsExpander expander = new DisabledReusableInputsExpander();

        List<Input<?>> inputs = List.of(
            ReusableInputsInput.builder().id("myBlock").type(Type.REUSABLE_INPUTS).ref("infra_request").build()
        );

        assertThatThrownBy(() -> expander.expand("main", "company", inputs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Enterprise Edition");
    }
}
