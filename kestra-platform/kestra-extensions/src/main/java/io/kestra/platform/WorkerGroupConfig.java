package io.kestra.platform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration for a worker group.
 *
 * Represents a logical group of workers that process tasks from specific namespaces.
 * Worker groups provide isolation, resource guarantees, and dedicated compute for clients.
 */
@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkerGroupConfig {

    /**
     * Unique identifier for the worker group
     */
    private UUID id;

    /**
     * Name of the worker group (e.g., "shared", "client1-cpu", "client2-gpu")
     */
    private String name;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * CPU resource limit (e.g., "2000m" for 2 cores)
     */
    private String resourceCpu;

    /**
     * Memory resource limit (e.g., "4Gi")
     */
    private String resourceMemory;

    /**
     * GPU resource specification (e.g., "nvidia.com/gpu=1")
     */
    private String resourceGpu;

    /**
     * Maximum number of tasks this worker group can run concurrently
     */
    private Integer maxConcurrentTasks;

    /**
     * Maximum number of tasks that can be queued
     */
    private Integer maxQueuedTasks;

    /**
     * Whether this worker group has GPU capabilities
     */
    private Boolean gpuEnabled;

    /**
     * Whether auto-scaling is enabled
     */
    private Boolean autoScalingEnabled;

    /**
     * Minimum number of worker replicas
     */
    private Integer minReplicas;

    /**
     * Maximum number of worker replicas
     */
    private Integer maxReplicas;

    /**
     * Current status (active, inactive, maintenance)
     */
    private WorkerGroupStatus status;

    /**
     * Timestamp when this worker group was created
     */
    private Instant createdAt;

    /**
     * Timestamp when this worker group was last updated
     */
    private Instant updatedAt;

    /**
     * User who created this worker group
     */
    private String createdBy;

    /**
     * Worker group status enumeration
     */
    public enum WorkerGroupStatus {
        ACTIVE,
        INACTIVE,
        MAINTENANCE
    }

    /**
     * Check if this worker group is currently accepting tasks
     */
    public boolean isActive() {
        return status == WorkerGroupStatus.ACTIVE;
    }

    /**
     * Check if this worker group has GPU support
     */
    public boolean hasGpu() {
        return Boolean.TRUE.equals(gpuEnabled);
    }

    /**
     * Get the Kafka topic name for this worker group
     */
    public String getKafkaTopic() {
        return "workergroup-" + name;
    }
}
