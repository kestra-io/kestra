package io.kestra.core.services;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.tasks.debugs.Return;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class FlowServiceTest {
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private FlowService flowService;

    private static Flow create(String flowId, String taskId, Integer revision) {
        return create(null, flowId, taskId, revision);
    }

    private static Flow create(String tenantId, String flowId, String taskId, Integer revision) {
        return Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tenantId(tenantId)
            .revision(revision)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format("test")
                .build()))
            .build();
    }

    @Test
    public void sameRevisionWithDeletedOrdered() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 2).toDeleted(),
            create("test", "test2", 4)
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getRevision(), is(4));
    }

    @Test
    public void sameRevisionWithDeletedSameRevision() {
        Stream<Flow> stream = Stream.of(
            create("test2", "test2", 1),
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test3", 3),
            create("test", "test2", 2).toDeleted()
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getId(), is("test2"));
    }

    @Test
    public void sameRevisionWithDeletedUnordered() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 4),
            create("test", "test2", 2).toDeleted()
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getRevision(), is(4));
    }

    @Test
    public void multipleFlow() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 2),
            create("test", "test2", 1),
            create("test2", "test2", 1),
            create("test2", "test3", 3),
            create("test3", "test1", 2),
            create("test3", "test2", 3)

        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test")).findFirst().orElseThrow().getRevision(), is(2));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test2")).findFirst().orElseThrow().getRevision(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test3")).findFirst().orElseThrow().getRevision(), is(3));
    }
}