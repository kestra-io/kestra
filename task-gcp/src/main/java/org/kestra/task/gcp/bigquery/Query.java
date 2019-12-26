package org.kestra.task.gcp.bigquery;

import com.google.cloud.bigquery.*;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Query extends Task implements RunnableTask {
    private String sql;

    @Builder.Default
    private boolean legacySql = false;

    @Builder.Default
    private boolean fetch = false;

    @Builder.Default
    private boolean fetchOne = false;

    private List<String> positionalParameters;

    private Map<String, String> namedParameters;

    private List<String> clusteringFields;

    private String destinationTable;

    private List<JobInfo.SchemaUpdateOption> schemaUpdateOptions;

    private String timePartitioningField;

    private JobInfo.WriteDisposition writeDisposition;

    private JobInfo.CreateDisposition createDisposition;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        BigQuery connection = new Connection().of();
        Logger logger = runContext.logger(this.getClass());
        String sql = runContext.render(this.sql);

        QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(sql)
            .setUseLegacySql(this.legacySql);

        if (this.clusteringFields != null) {
            builder.setClustering(Clustering.newBuilder().setFields(this.clusteringFields).build());
        }

        if (this.destinationTable != null) {
            builder.setDestinationTable(Connection.tableId(runContext.render(this.destinationTable)));
        }

        if (this.schemaUpdateOptions != null) {
            builder.setSchemaUpdateOptions(this.schemaUpdateOptions);
        }

        if (this.timePartitioningField != null) {
            builder.setTimePartitioning(TimePartitioning.newBuilder(TimePartitioning.Type.DAY)
                .setField(this.timePartitioningField)
                .build()
            );
        }

        if (this.writeDisposition != null) {
            builder.setWriteDisposition(this.writeDisposition);
        }

        if (this.createDisposition != null) {
            builder.setCreateDisposition(this.createDisposition);
        }

        QueryJobConfiguration jobConfiguration = builder.build();
        logger.debug("Starting query\n{}", JacksonMapper.log(jobConfiguration));

        Job queryJob = connection
            .create(JobInfo.newBuilder(jobConfiguration)
                .setJobId(Connection.jobId(runContext))
                .build()
            );

        Connection.handleErrors(queryJob, logger);
        queryJob = queryJob.waitFor();
        Connection.handleErrors(queryJob, logger);

        JobStatistics.QueryStatistics stats = queryJob.getStatistics();

        RunOutput.RunOutputBuilder output = RunOutput.builder();

        if (this.fetch || this.fetchOne) {
            TableResult result = queryJob.getQueryResults();

            if (fetch) {
                output.outputs(ImmutableMap.of("rows", this.fetchResult(result)));
            } else {
                output.outputs(ImmutableMap.of("row", this.fetchResult(result).get(0)));
            }
        }

        return output.build();
    }


    private List<Map<String, Object>> fetchResult(TableResult result) {
        return StreamSupport
            .stream(result.getValues().spliterator(), false)
            .map(fieldValues -> this.convertRows(result, fieldValues))
            .collect(Collectors.toList());
    }

    private Map<String, Object> convertRows(TableResult result, FieldValueList fieldValues) {
        HashMap<String, Object> row = new HashMap<>();
        result
            .getSchema()
            .getFields()
            .forEach(field -> {
                row.put(field.getName(), convertCell(field, fieldValues.get(field.getName()), false));
            });

        return row;
    }

    private Object convertCell(Field field, FieldValue value, boolean isRepeated) {
        if (field.getMode() == Field.Mode.REPEATED && !isRepeated) {
            return value
                .getRepeatedValue()
                .stream()
                .map(fieldValue -> this.convertCell(field, fieldValue, true))
                .collect(Collectors.toList());
        }

        if (value.isNull()) {
            return null;
        }

        if (LegacySQLTypeName.BOOLEAN.equals(field.getType())) {
            return value.getBooleanValue();
        }

        if (LegacySQLTypeName.BYTES.equals(field.getType())) {
            return value.getBytesValue();
        }

        if (LegacySQLTypeName.DATE.equals(field.getType())) {
            return LocalDate.parse(value.getStringValue());
        }

        if (LegacySQLTypeName.DATETIME.equals(field.getType())) {
            return Instant.parse(value.getStringValue() + "Z");
        }

        if (LegacySQLTypeName.FLOAT.equals(field.getType())) {
            return value.getDoubleValue();
        }

        if (LegacySQLTypeName.GEOGRAPHY.equals(field.getType())) {
            Pattern p = Pattern.compile("^POINT\\(([0-9.]+) ([0-9.]+)\\)$");
            Matcher m = p.matcher(value.getStringValue());

            if (m.find()) {
                return Arrays.asList(
                    Double.parseDouble(m.group(1)),
                    Double.parseDouble(m.group(2))
                );
            }

            throw new IllegalFormatFlagsException("Couldn't match '" + value.getStringValue() + "'");
        }

        if (LegacySQLTypeName.INTEGER.equals(field.getType())) {
            return value.getLongValue();
        }

        if (LegacySQLTypeName.NUMERIC.equals(field.getType())) {
            return value.getDoubleValue();
        }

        if (LegacySQLTypeName.RECORD.equals(field.getType())) {
            AtomicInteger counter = new AtomicInteger(0);

            return field
                .getSubFields()
                .stream()
                .map(sub -> new AbstractMap.SimpleEntry<>(
                    sub.getName(),
                    this.convertCell(sub, value.getRepeatedValue().get(counter.get()), false)
                ))
                .peek(u -> counter.getAndIncrement())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        if (LegacySQLTypeName.STRING.equals(field.getType())) {
            return value.getStringValue();
        }

        if (LegacySQLTypeName.TIME.equals(field.getType())) {
            return LocalTime.parse(value.getStringValue());
        }

        if (LegacySQLTypeName.TIMESTAMP.equals(field.getType())) {
            return Instant.ofEpochMilli(value.getTimestampValue() / 1000);
        }

        throw new IllegalArgumentException("Invalid type '" + field.getType() + "'");
    }

}
