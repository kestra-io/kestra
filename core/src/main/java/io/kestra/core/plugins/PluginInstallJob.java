package io.kestra.core.plugins;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an async plugin installation job submitted via {@link PluginInstallJobRegistry}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginInstallJob(
    UUID id,
    Status status,
    List<PluginArtifact> artifacts,
    Map<String, ArtifactProgress> progress,
    Instant startedAt,
    Instant finishedAt,
    String error) {

    /** Overall job lifecycle status. */
    public enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    /** Per-artifact transfer state reported by the Aether TransferListener. */
    public record ArtifactProgress(
        String resource,
        long transferred,
        long total,
        ArtifactState state) {
    }

    /** State of a single artifact transfer. */
    public enum ArtifactState {
        STARTED,
        PROGRESSING,
        SUCCEEDED,
        FAILED
    }

    /** Creates a new job in {@link Status#PENDING}. */
    public static PluginInstallJob pending(final List<PluginArtifact> artifacts) {
        return new PluginInstallJob(
            UUID.randomUUID(),
            Status.PENDING,
            List.copyOf(artifacts),
            new ConcurrentHashMap<>(),
            null,
            null,
            null
        );
    }

    /** Returns a copy of this job with {@link Status#RUNNING} and the provided start time. */
    public PluginInstallJob running(final Instant startedAt) {
        return new PluginInstallJob(id, Status.RUNNING, artifacts, progress, startedAt, null, null);
    }

    /** Returns a copy of this job with {@link Status#SUCCEEDED} and the provided finish time. */
    public PluginInstallJob succeeded(final Instant finishedAt) {
        return new PluginInstallJob(id, Status.SUCCEEDED, artifacts, progress, startedAt, finishedAt, null);
    }

    /** Returns a copy of this job with {@link Status#FAILED}, the provided finish time and error message. */
    public PluginInstallJob failed(final Instant finishedAt, final String error) {
        return new PluginInstallJob(id, Status.FAILED, artifacts, progress, startedAt, finishedAt, error);
    }

    /** Returns {@code true} if this job has reached a terminal state. */
    public boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }
}
