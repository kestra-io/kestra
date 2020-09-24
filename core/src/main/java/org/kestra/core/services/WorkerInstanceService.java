package org.kestra.core.services;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.runners.WorkerInstance;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;

/**
 * Provides business logic to manipulate {@link WorkerInstance}
 */
@Singleton
@Slf4j
public class WorkerInstanceService {
    public static WorkerInstance removeEvictedPartitions(Stream<WorkerInstance> stream, WorkerInstance incoming) {
        // looking for WorkerInstance that of common partition
        List<WorkerInstance> changedInstance = stream
            .filter(r -> !r.getWorkerUuid().toString().equals(incoming.getWorkerUuid().toString()))
            .filter(r -> !Collections.disjoint(r.getPartitions(), incoming.getPartitions()))
            .collect(Collectors.toList());

        // we received one WorkerInstance by one, we can't have multiple changed at the same time
        if (changedInstance.size() > 1) {
            throw new RuntimeException("Too many instance changed, got " + changedInstance.size() + ": " + changedInstance);
        }

        // found a WorkerInstance with partitions reassigned, we remove the partitions
        if (changedInstance.size() == 1) {
            WorkerInstance evictedInstance = changedInstance.get(0);

            evictedInstance.getPartitions().removeAll(incoming.getPartitions());

            return evictedInstance;
        }

        return null;
    }
}
