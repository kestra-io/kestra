package io.kestra.webserver.controllers.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.ZonedDateTime;

@JsonPropertyOrder({
    "version",
    "commit",
    "commitDate",
    "type"
})
public record ServerInfo(
    String version,
    String commit,
    ZonedDateTime commitDate,
    String type
) {
}
