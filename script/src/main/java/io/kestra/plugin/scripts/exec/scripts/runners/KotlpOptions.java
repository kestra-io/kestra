package io.kestra.plugin.scripts.exec.scripts.runners;

import java.time.Duration;

import lombok.Builder;

/**
 * Options controlling the <a href="https://github.com/kestra-io/kotlp">kotlp</a> command wrapper.
 *
 * @param enabled          whether the commands are wrapped with the kotlp binary
 * @param logDir           directory passed as {@code --log-dir} where kotlp mirrors every telemetry record as bare OTLP NDJSON;
 *                         a relative path resolves against the working directory in every runner
 * @param logFlushInterval passed as {@code --log-flush-interval}, in seconds; rotates the log dir file on this interval
 *                         into {@code log-1.ndjson}, {@code log-2.ndjson}, etc. Requires {@code logDir} to be set.
 */
@Builder
public record KotlpOptions(Boolean enabled, String logDir, Duration logFlushInterval) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }
}
