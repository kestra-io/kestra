package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TaskDefaultServiceTest {
    @Inject
    private TaskDefaultService taskDefaultService;

    @Test
    public void injectFlowAndGlobals() {
        DefaultTester task = DefaultTester.builder()
            .id("test")
            .type(DefaultTester.class.getName())
            .set(666)
            .build();

        Flow flow = Flow.builder()
            .tasks(Collections.singletonList(task))
            .taskDefaults(Collections.singletonList(new TaskDefault(DefaultTester.class.getName(), ImmutableMap.of(
                "value", 1,
                "set", 123,
                "arrays", Collections.singletonList(1)
            ))))
            .build();

        Flow injected = taskDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().get(0)).getValue(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getDoubleValue(), is(19D));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getArrays().size(), is(2));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getArrays(), containsInAnyOrder(1, 2));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getHere(), is("me"));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().get(0).getVal().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().get(0).getVal().get("key"), is("test"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DefaultTester extends Task implements RunnableTask<VoidOutput> {
        private Collections property;

        private Integer value;

        private Double doubleValue;

        private Integer set;

        private List<Integer> arrays;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            return null;
        }

        @NoArgsConstructor
        @Getter
        public static class Collections {
            private String here;
            private List<Lists> lists;

        }

        @NoArgsConstructor
        @Getter
        public static class Lists {
            private Map<String, String> val;
        }
    }
}