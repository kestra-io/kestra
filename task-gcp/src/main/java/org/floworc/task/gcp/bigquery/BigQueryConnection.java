package org.floworc.task.gcp.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.floworc.task.gcp.AbstractConnection;

public class BigQueryConnection extends AbstractConnection {
    public BigQuery of() {
        return BigQueryOptions.getDefaultInstance().getService();
    }

    public BigQuery of(String serviceAccount) {
        return BigQueryOptions
            .newBuilder()
            .setCredentials(this.credentials(serviceAccount))
            .build()
            .getService();
    }
}
