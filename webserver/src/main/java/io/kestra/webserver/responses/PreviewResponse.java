package io.kestra.webserver.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class PreviewResponse {
    String extension;

    String type;

    String content;
}
