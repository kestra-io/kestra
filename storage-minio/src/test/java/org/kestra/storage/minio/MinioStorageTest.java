package org.kestra.storage.minio;

import io.minio.MinioClient;
import org.kestra.core.storages.AbstractLocalStorageTest;
import org.junit.jupiter.api.BeforeEach;

import javax.inject.Inject;

class MinioStorageTest extends AbstractLocalStorageTest {
    @Inject
    MinioClientFactory clientFactory;

    @Inject
    MinioConfig config;

    @BeforeEach
    void init() throws Exception {
        MinioClient client = clientFactory.of(this.config);

        if (!client.bucketExists(config.getBucket())) {
            client.makeBucket(config.getBucket());
        }
    }
}