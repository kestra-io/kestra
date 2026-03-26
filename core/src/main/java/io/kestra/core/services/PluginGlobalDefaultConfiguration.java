package io.kestra.core.services;

import java.util.List;

import io.kestra.core.models.flows.PluginDefault;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

@ConfigurationProperties(value = "kestra.plugins", includes = "defaults")
@Getter
public class PluginGlobalDefaultConfiguration {
    List<PluginDefault> defaults;
}
