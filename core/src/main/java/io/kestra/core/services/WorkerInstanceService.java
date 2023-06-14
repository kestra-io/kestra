package io.kestra.core.services;

import lombok.extern.slf4j.Slf4j;
import io.kestra.core.runners.WorkerInstance;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;

/**
 * Provides business logic to manipulate {@link WorkerInstance}
 */
@Singleton
@Slf4j
public class WorkerInstanceService {
    public static List<WorkerInstance> removeEvictedPartitions(Stream<WorkerInstance> stream, WorkerInstance incoming) {
        // looking for WorkerInstance that of common partition
        List<WorkerInstance> changedInstance = stream
            .filter(r -> !r.getWorkerUuid().toString().equals(incoming.getWorkerUuid().toString()))
            .filter(r -> (r.getWorkerGroup() == null && incoming.getWorkerGroup() == null) ||
                (r.getWorkerGroup() != null && r.getWorkerGroup().equals(incoming.getWorkerGroup()))
            )
            .filter(r -> !Collections.disjoint(r.getPartitions() == null ? List.of() : r.getPartitions(), incoming.getPartitions() == null ? List.of() : incoming.getPartitions()))
            .toList();

        if (!changedInstance.isEmpty()) {
            return changedInstance
                .stream()
                .filter(r -> r.getPartitions() != null)
                .peek(evictedInstance -> evictedInstance.getPartitions().removeAll(incoming.getPartitions()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
