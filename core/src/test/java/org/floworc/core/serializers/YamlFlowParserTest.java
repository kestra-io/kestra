package org.floworc.core.serializers;

import org.junit.jupiter.api.Test;
import org.floworc.core.Utils;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.tasks.RetryIntervalType;
import org.floworc.core.models.tasks.Task;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlFlowParserTest {
    @Test
    void parse() throws IOException {
        Flow flow = Utils.parse("flows/valids/full.yaml");

        assertThat(flow.getId(), is("full"));
        assertThat(flow.getTasks().size(), is(5));

        // third with all optionnals
        Task optionnals = flow.getTasks().get(2);
        assertThat(optionnals.getTimeout(), is(1000));
        assertThat(optionnals.getRetry().getInterval().getSeconds(), is(900L));
        assertThat(optionnals.getRetry().getType(), is(RetryIntervalType.CONSTANT));
        assertThat(optionnals.getRetry().getLimit(), is(5));
    }

    @Test
    void validation() throws IOException {
        assertThrows(InvalidDefinitionException.class, () -> {
            Utils.parse("flows/invalids/invalid.yaml");
        });

        try {
            Utils.parse("flows/invalids/invalid.yaml");
        } catch (InvalidDefinitionException e) {
            assertThat(e.getViolations().size(), is(3));
        }
    }
}