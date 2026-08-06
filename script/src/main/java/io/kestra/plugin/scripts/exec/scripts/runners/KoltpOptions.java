package io.kestra.plugin.scripts.exec.scripts.runners;

import lombok.Builder;

/**
 * Options controlling the <a href="https://github.com/kestra-io/koltp">koltp</a> command wrapper.
 *
 * @param enabled whether the commands are wrapped with the koltp binary
 * @param logDir  directory passed as {@code --log-dir} where koltp mirrors every telemetry record as bare OTLP NDJSON;
 *                a relative path resolves against the working directory in every runner
 */
@Builder
public record KoltpOptions(Boolean enabled, String logDir) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }
}
