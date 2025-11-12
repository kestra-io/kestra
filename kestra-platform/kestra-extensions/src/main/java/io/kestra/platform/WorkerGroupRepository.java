package io.kestra.platform;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for loading worker group configurations from the database.
 *
 * Handles all database operations related to worker groups and namespace mappings.
 * NO hardcoded values - all configuration comes from database which is populated
 * via environment-driven migrations.
 */
@Slf4j
@Singleton
@Requires(bean = DataSource.class)
public class WorkerGroupRepository {

    private final DataSource dataSource;

    public WorkerGroupRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("WorkerGroupRepository initialized");
    }

    /**
     * Load all active worker groups from database
     */
    public List<WorkerGroupConfig> findAllActive() {
        List<WorkerGroupConfig> workerGroups = new ArrayList<>();

        String sql = """
            SELECT id, name, description, resource_cpu, resource_memory, resource_gpu,
                   max_concurrent_tasks, max_queued_tasks, gpu_enabled, auto_scaling_enabled,
                   min_replicas, max_replicas, status, created_at, updated_at, created_by
            FROM worker_groups
            WHERE status = 'active'
            ORDER BY name
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                workerGroups.add(mapRowToWorkerGroupConfig(rs));
            }

            log.debug("Loaded {} active worker groups", workerGroups.size());

        } catch (SQLException e) {
            log.error("Error loading worker groups from database", e);
        }

        return workerGroups;
    }

    /**
     * Find worker group by name
     */
    public Optional<WorkerGroupConfig> findByName(String name) {
        String sql = """
            SELECT id, name, description, resource_cpu, resource_memory, resource_gpu,
                   max_concurrent_tasks, max_queued_tasks, gpu_enabled, auto_scaling_enabled,
                   min_replicas, max_replicas, status, created_at, updated_at, created_by
            FROM worker_groups
            WHERE name = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToWorkerGroupConfig(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Error finding worker group by name: {}", name, e);
        }

        return Optional.empty();
    }

    /**
     * Load all enabled namespace mappings
     */
    public List<NamespaceMapping> findAllEnabledMappings() {
        List<NamespaceMapping> mappings = new ArrayList<>();

        String sql = """
            SELECT id, namespace_pattern, worker_group_id, priority,
                   description, enabled, created_at, updated_at
            FROM namespace_worker_groups
            WHERE enabled = true
            ORDER BY priority DESC, namespace_pattern
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                mappings.add(mapRowToNamespaceMapping(rs));
            }

            log.debug("Loaded {} enabled namespace mappings", mappings.size());

        } catch (SQLException e) {
            log.error("Error loading namespace mappings from database", e);
        }

        return mappings;
    }

    /**
     * Find worker group for a given namespace
     *
     * @param namespace The namespace to match
     * @return Worker group config if a matching pattern is found
     */
    public Optional<WorkerGroupConfig> findWorkerGroupForNamespace(String namespace) {
        // Use PostgreSQL regex matching for efficiency
        String sql = """
            SELECT wg.id, wg.name, wg.description, wg.resource_cpu, wg.resource_memory,
                   wg.resource_gpu, wg.max_concurrent_tasks, wg.max_queued_tasks,
                   wg.gpu_enabled, wg.auto_scaling_enabled, wg.min_replicas, wg.max_replicas,
                   wg.status, wg.created_at, wg.updated_at, wg.created_by
            FROM worker_groups wg
            JOIN namespace_worker_groups nwg ON wg.id = nwg.worker_group_id
            WHERE nwg.enabled = true
              AND ? ~ nwg.namespace_pattern
              AND wg.status = 'active'
            ORDER BY nwg.priority DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, namespace);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    WorkerGroupConfig config = mapRowToWorkerGroupConfig(rs);
                    log.debug("Matched namespace '{}' to worker group '{}'", namespace, config.getName());
                    return Optional.of(config);
                }
            }

        } catch (SQLException e) {
            log.error("Error finding worker group for namespace: {}", namespace, e);
        }

        log.debug("No worker group match found for namespace: {}", namespace);
        return Optional.empty();
    }

    /**
     * Map database row to WorkerGroupConfig object
     */
    private WorkerGroupConfig mapRowToWorkerGroupConfig(ResultSet rs) throws SQLException {
        return WorkerGroupConfig.builder()
            .id(UUID.fromString(rs.getString("id")))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .resourceCpu(rs.getString("resource_cpu"))
            .resourceMemory(rs.getString("resource_memory"))
            .resourceGpu(rs.getString("resource_gpu"))
            .maxConcurrentTasks(rs.getInt("max_concurrent_tasks"))
            .maxQueuedTasks(rs.getInt("max_queued_tasks"))
            .gpuEnabled(rs.getBoolean("gpu_enabled"))
            .autoScalingEnabled(rs.getBoolean("auto_scaling_enabled"))
            .minReplicas(rs.getInt("min_replicas"))
            .maxReplicas(rs.getInt("max_replicas"))
            .status(WorkerGroupConfig.WorkerGroupStatus.valueOf(
                rs.getString("status").toUpperCase()
            ))
            .createdAt(convertTimestamp(rs.getTimestamp("created_at")))
            .updatedAt(convertTimestamp(rs.getTimestamp("updated_at")))
            .createdBy(rs.getString("created_by"))
            .build();
    }

    /**
     * Map database row to NamespaceMapping object
     */
    private NamespaceMapping mapRowToNamespaceMapping(ResultSet rs) throws SQLException {
        return NamespaceMapping.builder()
            .id(UUID.fromString(rs.getString("id")))
            .namespacePattern(rs.getString("namespace_pattern"))
            .workerGroupId(UUID.fromString(rs.getString("worker_group_id")))
            .priority(rs.getInt("priority"))
            .description(rs.getString("description"))
            .enabled(rs.getBoolean("enabled"))
            .createdAt(convertTimestamp(rs.getTimestamp("created_at")))
            .updatedAt(convertTimestamp(rs.getTimestamp("updated_at")))
            .build();
    }

    /**
     * Convert SQL Timestamp to Instant
     */
    private java.time.Instant convertTimestamp(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    /**
     * Check if database tables exist
     */
    public boolean tablesExist() {
        String sql = """
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_name = 'worker_groups'
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            log.error("Error checking if tables exist", e);
        }

        return false;
    }
}
