package io.kestra.core.storages;

import lombok.Value;

import java.util.Map;

@Value
public class StorageConfiguration {
    Map<String, Object> configuration;
}
