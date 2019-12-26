package org.kestra.task.gcp.gcs;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.kestra.task.gcp.AbstractConnection;

public class Connection extends AbstractConnection {
    public Storage of() {
        return StorageOptions.getDefaultInstance().getService();
    }

    public Storage of(String serviceAccount) {
        return StorageOptions
            .newBuilder()
            .setCredentials(this.credentials(serviceAccount))
            .build()
            .getService();
    }
}
