package org.kestra.storage.gcs;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import javax.inject.Singleton;

@Singleton
@Getter
@ConfigurationProperties("kestra.storage.gcs")
public class GcsConfig {
    String bucket;
}