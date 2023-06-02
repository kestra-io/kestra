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
        if (incoming.getPartitions().isEmpty()) {
            return Collections.emptyList();
        }

        // looking for WorkerInstance that of common partition
        List<WorkerInstance> changedInstance = stream
            .filter(r -> !r.getWorkerUuid().toString().equals(incoming.getWorkerUuid().toString()))
            .filter(r -> !Collections.disjoint(r.getPartitions() == null ? Collections.emptyList() : r.getPartitions(), incoming.getPartitions()))
            .toList();

        if (changedInstance.size() >= 1) {
            return changedInstance
                .stream()
                .peek(evictedInstance -> evictedInstance.getPartitions().removeAll(incoming.getPartitions()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
