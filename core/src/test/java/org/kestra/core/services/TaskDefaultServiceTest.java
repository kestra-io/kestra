package org.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.TaskDefault;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.runners.RunContext;

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

    @Inject
    private ApplicationContext applicationContext;

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

        DefaultTester injected = taskDefaultService.injectDefaults(task, flow);

        assertThat(injected.getValue(), is(1));
        assertThat(injected.getSet(), is(666));
        assertThat(injected.getDoubleValue(), is(19D));
        assertThat(injected.getArrays().size(), is(2));
        assertThat(injected.getArrays(), containsInAnyOrder(1, 2));
        assertThat(injected.getProperty().getHere(), is("me"));
        assertThat(injected.getProperty().getLists().size(), is(1));
        assertThat(injected.getProperty().getLists().get(0).getVal().size(), is(1));
        assertThat(injected.getProperty().getLists().get(0).getVal().get("key"), is("test"));
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