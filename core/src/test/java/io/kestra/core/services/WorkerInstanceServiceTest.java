package io.kestra.core.services;

import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.WorkerInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class WorkerInstanceServiceTest {
    @Test
    void removeEvictedPartitions() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3));

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            Stream.of(first),
            workerInstance(Arrays.asList(1, 2, 3))
        );

        assertThat(workerInstance.size(), is(1));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getPartitions().size(), is(0));
    }

    @Test
    void removeEvictedPartitionsWithWorkerGroup() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3), "workerGroup");

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            Stream.of(first),
            workerInstance(Arrays.asList(1, 2, 3), "workerGroup")
        );

        assertThat(workerInstance.size(), is(1));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getWorkerGroup(), is("workerGroup"));
        assertThat(workerInstance.get(0).getPartitions().size(), is(0));
    }

    @Test
    void removeEvictedPartitionsMultiple() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3));
        WorkerInstance willBeUpdated = workerInstance(Arrays.asList(4, 5, 6));

        Stream<WorkerInstance> workerInstanceStream = Stream.of(
            first,
            willBeUpdated
        );

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            workerInstanceStream,
            workerInstance(Arrays.asList(1, 2, 3, 4, 5, 6), willBeUpdated.getWorkerUuid(), null)
        );

        assertThat(workerInstance.size(), is(1));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getPartitions().size(), is(0));
    }

    @Test
    void removeEvictedPartitionsMultipleWithWorkerGroup() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3), "workerGroup");
        WorkerInstance willBeUpdated = workerInstance(Arrays.asList(4, 5, 6), "workerGroup");

        Stream<WorkerInstance> workerInstanceStream = Stream.of(
            first,
            willBeUpdated
        );

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            workerInstanceStream,
            workerInstance(Arrays.asList(1, 2, 3, 4, 5, 6), willBeUpdated.getWorkerUuid(), "workerGroup")
        );

        assertThat(workerInstance.size(), is(1));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getWorkerGroup(), is("workerGroup"));
        assertThat(workerInstance.get(0).getPartitions().size(), is(0));
    }


    @Test
    void removeMultiplePartition() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3));
        WorkerInstance second = workerInstance(Arrays.asList(4, 5, 6));

        Stream<WorkerInstance> workerInstanceStream = Stream.of(
            first,
            second
        );

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            workerInstanceStream,
            workerInstance(Arrays.asList(2, 3, 4))
        );

        assertThat(workerInstance.size(), is(2));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getPartitions().size(), is(1));
        assertThat(workerInstance.get(0).getPartitions(), contains(1));

        assertThat(workerInstance.get(1).getWorkerUuid(), is(second.getWorkerUuid()));
        assertThat(workerInstance.get(1).getPartitions().size(), is(2));
        assertThat(workerInstance.get(1).getPartitions(), contains(5, 6));
    }

    @Test
    void removeMultiplePartitionWithWorkerGroup() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3), "workerGroup");
        WorkerInstance second = workerInstance(Arrays.asList(4, 5, 6), "workerGroup");

        Stream<WorkerInstance> workerInstanceStream = Stream.of(
            first,
            second
        );

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            workerInstanceStream,
            workerInstance(Arrays.asList(2, 3, 4), "workerGroup")
        );

        assertThat(workerInstance.size(), is(2));
        assertThat(workerInstance.get(0).getWorkerUuid(), is(first.getWorkerUuid()));
        assertThat(workerInstance.get(0).getWorkerGroup(), is("workerGroup"));
        assertThat(workerInstance.get(0).getPartitions().size(), is(1));
        assertThat(workerInstance.get(0).getPartitions(), contains(1));

        assertThat(workerInstance.get(1).getWorkerUuid(), is(second.getWorkerUuid()));
        assertThat(workerInstance.get(1).getWorkerGroup(), is("workerGroup"));
        assertThat(workerInstance.get(1).getPartitions().size(), is(2));
        assertThat(workerInstance.get(1).getPartitions(), contains(5, 6));
    }

    @Test
    void dontRemoveEvictedPartitionsWithWorkerGroup() {
        WorkerInstance first = workerInstance(Arrays.asList(1, 2, 3));

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            Stream.of(first),
            workerInstance(Arrays.asList(1, 2, 3), "workerGroup")
        );

        assertThat(workerInstance.size(), is(0));
    }


    @Test
    void nullCheks() {
        WorkerInstance first = WorkerInstance
            .builder()
            .partitions(null)
            .workerUuid(IdUtils.create())
            .build();

        List<WorkerInstance> workerInstance = WorkerInstanceService.removeEvictedPartitions(
            Stream.of(first),
            workerInstance(Arrays.asList(1, 2, 3), "workerGroup")
        );

        assertThat(workerInstance.size(), is(0));
    }

    private WorkerInstance workerInstance(List<Integer> partitions) {
        return workerInstance(partitions, null, null);
    }

    private WorkerInstance workerInstance(List<Integer> partitions, String workerGroup) {
        return workerInstance(partitions, null, workerGroup);
    }

    private static WorkerInstance workerInstance(List<Integer> partitions, String uuid, String workerGroup) {
        return WorkerInstance
            .builder()
            .partitions(new ArrayList<>(partitions))
            .workerUuid(uuid == null ? IdUtils.create() : uuid)
            .workerGroup(workerGroup)
            .build();
    }
}
