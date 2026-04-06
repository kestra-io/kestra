package io.kestra.webserver.controllers.domain;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(
    {
        "version",
        "commit",
        "commitDate",
        "type"
    }
)
public record ServerInfo(
    String version,
    String commit,
    ZonedDateTime commitDate,
    String type) {
}
