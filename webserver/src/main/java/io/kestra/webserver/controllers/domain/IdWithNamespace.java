package io.kestra.webserver.controllers.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdWithNamespace {
    private String namespace;
    private String id;
}
