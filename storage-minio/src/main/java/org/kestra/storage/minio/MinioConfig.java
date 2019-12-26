package org.kestra.storage.minio;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import javax.inject.Singleton;

@Singleton
@Getter
@ConfigurationProperties("kestra.storage.minio")
public class MinioConfig {
    String endpoint;

    int port;

    String accessKey;

    String secretKey;

    String region;

    boolean secure;

    String bucket;
}