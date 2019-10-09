package org.floworc.storage.gcs;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import javax.inject.Singleton;

@Singleton
@Getter
@ConfigurationProperties("floworc.storage.gcs")
public class GcsConfig {
    String bucket;
}