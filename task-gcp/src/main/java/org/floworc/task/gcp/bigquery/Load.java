package org.floworc.task.gcp.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.TableDataWriteChannel;
import com.google.cloud.bigquery.WriteChannelConfiguration;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Load extends AbstractLoad implements RunnableTask {
    private String from;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        BigQuery connection = new Connection().of();
        Logger logger = runContext.logger(this.getClass());

        WriteChannelConfiguration.Builder builder = WriteChannelConfiguration
            .newBuilder(Connection.tableId(this.destinationTable));

        this.setOptions(builder);

        WriteChannelConfiguration configuration = builder.build();
        logger.debug("Starting load\n{}", JacksonMapper.log(configuration));

        URI from = new URI(runContext.render(this.from));
        InputStream data = runContext.uriToInputStream(from);

        TableDataWriteChannel writer = connection.writer(configuration);
        try (OutputStream stream = Channels.newOutputStream(writer)) {
            byte[] buffer = new byte[10_240];

            int limit;
            while ((limit = data.read(buffer)) >= 0) {
                writer.write(ByteBuffer.wrap(buffer, 0, limit));
            }
        }

        return this.execute(logger, configuration, writer.getJob());
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
