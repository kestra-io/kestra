package io.kestra.plugin.scripts.exec.scripts.runners;

import java.time.Duration;

import lombok.Builder;

/**
 * Options controlling the <a href="https://github.com/kestra-io/kotlp">kotlp</a> command wrapper.
 *
 * @param enabled          whether the commands are wrapped with the kotlp binary. On its own, kotlp frames every
 *                         telemetry record on the console as {@code ::{"otlp":<json>}::}, which
 *                         {@link io.kestra.core.models.tasks.runners.TaskLogLineMatcher} picks up
 * @param logDir           directory passed as {@code --log-dir}, where kotlp writes every telemetry record to
 *                         {@code log.ndjson} as bare OTLP NDJSON; a relative path resolves against the working
 *                         directory in every runner. The directory is then the only full copy: kotlp drops the console
 *                         framing and prints the child's output raw instead, so telemetry no longer reaches Kestra
 *                         through the log lines and a caller setting this must ship every file in the directory back
 *                         and feed each to {@link io.kestra.core.models.tasks.runners.TaskLogLineMatcher#parseOtlp}
 * @param logFlushInterval passed as {@code --log-flush-interval}, in seconds; requires {@code logDir} to be set. The
 *                         records are then spread over {@code log-1.ndjson}, {@code log-2.ndjson}, … rather than
 *                         accumulating in one file — and there is no {@code log.ndjson} at all, so a caller collecting
 *                         that one fixed name would come back empty
 */
@Builder
public record KotlpOptions(Boolean enabled, String logDir, Duration logFlushInterval) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }
}
