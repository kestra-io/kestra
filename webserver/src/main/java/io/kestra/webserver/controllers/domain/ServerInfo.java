package io.kestra.webserver.controllers.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
    "version",
    "commit",
    "type"
})
public record ServerInfo(
    String version,
    String commit,
    String type
) {
}
