package io.kestra.plugin.scripts.exec.scripts.runners;

import java.time.Duration;

import lombok.Builder;

/**
 * Options controlling the <a href="https://github.com/kestra-io/kotlp">kotlp</a> command wrapper.
 *
 * @param enabled          whether the commands are wrapped with the kotlp binary. On its own, kotlp frames every
 *                         telemetry record on the console as {@code ::{"otlp":<json>}::}, which
 *                         {@link io.kestra.core.models.tasks.runners.TaskLogLineMatcher} picks up
 * @param logDir           directory passed as {@code --log-dir}, where kotlp writes every telemetry record as bare
 *                         OTLP NDJSON; a relative path resolves against the working directory in every runner.
 *                         The file is then the only full copy: kotlp drops the console framing and prints the child's
 *                         output raw instead, so telemetry no longer reaches Kestra through the log lines and a caller
 *                         setting this must ship the file back and feed it to
 *                         {@link io.kestra.core.models.tasks.runners.TaskLogLineMatcher#parseOtlp}
 * @param logFlushInterval passed as {@code --log-flush-interval}, in seconds; rotates the log dir file on this interval
 *                         into {@code log-1.ndjson}, {@code log-2.ndjson}, etc. Requires {@code logDir} to be set.
 */
@Builder
public record KotlpOptions(Boolean enabled, String logDir, Duration logFlushInterval) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }
}
