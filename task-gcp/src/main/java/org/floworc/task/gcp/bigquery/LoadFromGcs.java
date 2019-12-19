package org.floworc.task.gcp.bigquery;

import com.google.cloud.bigquery.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class LoadFromGcs extends AbstractLoad implements RunnableTask {
    /**
     * Sets the fully-qualified URIs that point to source data in Google Cloud Storage (e.g.
     * gs://bucket/path). Each URI can contain one '*' wildcard character and it must come after the
     * 'bucket' name.
     */
    private List<String> from;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        BigQuery connection = new Connection().of();
        Logger logger = runContext.logger(this.getClass());

        List<String> from = runContext.render(this.from);

        LoadJobConfiguration.Builder builder = LoadJobConfiguration
            .newBuilder(Connection.tableId(runContext.render(this.destinationTable)), from);

        this.setOptions(builder);

        LoadJobConfiguration configuration = builder.build();
        Job loadJob = connection.create(JobInfo.of(configuration));

        logger.debug("Starting query\n{}", JacksonMapper.log(configuration));

        return this.execute(logger, configuration, loadJob);
    }

    public enum Format {
        CSV,
        JSON,
        AVRO,
        PARQUET,
        ORC,
        // GOOGLE_SHEETS,
        // BIGTABLE,
        // DATASTORE_BACKUP,
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvOptions {
        private Boolean allowJaggedRows;
        private Boolean allowQuotedNewLines;
        private String encoding;
        private String fieldDelimiter;
        private String quote;
        private Long skipLeadingRows;

        private com.google.cloud.bigquery.CsvOptions to() {
            com.google.cloud.bigquery.CsvOptions.Builder builder = com.google.cloud.bigquery.CsvOptions.newBuilder();

            if (this.allowJaggedRows != null) {
                builder.setAllowJaggedRows(this.allowJaggedRows);
            }

            if (this.allowQuotedNewLines != null) {
                builder.setAllowQuotedNewLines(this.allowQuotedNewLines);
            }

            if (this.encoding != null) {
                builder.setEncoding(this.encoding);
            }

            if (this.fieldDelimiter != null) {
                builder.setFieldDelimiter(this.fieldDelimiter);
            }

            if (this.quote != null) {
                builder.setQuote(this.quote);
            }

            if (this.skipLeadingRows != null) {
                builder.setSkipLeadingRows(this.skipLeadingRows);
            }

            return builder.build();
        }
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvroOptions {
        private Boolean useAvroLogicalTypes;
    }
}
