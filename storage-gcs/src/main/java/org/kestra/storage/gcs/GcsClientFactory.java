package org.kestra.storage.gcs;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
@GcsStorageEnabled
public class GcsClientFactory {
    @Singleton
    public Storage of(GcsConfig config) {
        return StorageOptions.getDefaultInstance().getService();
    }
}
