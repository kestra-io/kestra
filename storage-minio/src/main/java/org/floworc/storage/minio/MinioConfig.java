package org.floworc.storage.minio;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import javax.inject.Singleton;

@Singleton
@Getter
@ConfigurationProperties("floworc.storage.minio")
public class MinioConfig {
    String endpoint;

    int port;

    String accessKey;

    String secretKey;

    String region;

    boolean secure;

    String bucket;
}