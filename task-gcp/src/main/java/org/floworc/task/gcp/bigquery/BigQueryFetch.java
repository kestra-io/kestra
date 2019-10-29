package org.floworc.task.gcp.bigquery;

import com.google.cloud.bigquery.*;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.slf4j.Logger;

import java.io.IOException;
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
public class BigQueryFetch extends Task implements RunnableTask {
    private String sql;
    @Builder.Default
    private boolean legacySql = false;
    private List<String> positionalParameters;
    private Map<String, String> namedParameters;

    @Builder.Default
    private transient BigQuery connection = new BigQueryConnection().of();

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());
        String sql = runContext.render(this.sql);

        logger.debug("Starting query '{}'", sql);

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
            .setUseLegacySql(this.legacySql)
            //.setPositionalParameters(this.positionalParameters)
            //.setNamedParameters(this.namedParameters)
            .build();

        // @TODO: named based on execution
        JobId jobId = JobId.of(UUID.randomUUID().toString());
        Job queryJob = connection.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());
        queryJob = queryJob.waitFor();

//        JobStatistics.LoadStatistics stats = queryJob.getStatistics();

        this.handleErrors(queryJob, logger);

        TableResult result = queryJob.getQueryResults();

        return RunOutput
            .builder()
            .outputs(ImmutableMap.of("rows", this.fetchResult(result)))
            .build();
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

    private void handleErrors(Job queryJob, Logger logger) throws IOException {
        if (queryJob == null) {
            throw new IllegalArgumentException("Job no longer exists");
        } else if (queryJob.getStatus().getError() != null) {

            queryJob
                .getStatus()
                .getExecutionErrors()
                .forEach(bigQueryError -> {
                    logger.error(
                        "Error query with error [\n - {}\n]",
                        bigQueryError.toString()
                    );
                });

            throw new IOException(queryJob.getStatus().getError().toString());
        }
    }
}
