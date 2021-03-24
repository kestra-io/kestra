package io.kestra.core.plugins;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.net.URL;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ExternalPlugin {
    private final URL location;
    private final URL[] resources;
}
