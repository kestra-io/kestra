package org.kestra.task.gcp.bigquery;

import com.devskiller.friendly_id.FriendlyId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.TimePartitioning;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.Utils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@MicronautTest
class QueryTest {
    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.tasks.bigquery.project}")
    private String project;

    @Value("${kestra.tasks.bigquery.dataset}")
    private String dataset;

    @Test
    @SuppressWarnings("unchecked")
    void fetch() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "sql", "SELECT " +
                    "  \"hello\" as string," +
                    "  NULL AS `nullable`," +
                    "  1 as int," +
                    "  1.25 AS float," +
                    "  DATE(\"2008-12-25\") AS date," +
                    "  DATETIME \"2008-12-25 15:30:00.123456\" AS datetime," +
                    "  TIME(DATETIME \"2008-12-25 15:30:00.123456\") AS time," +
                    "  TIMESTAMP(\"2008-12-25 15:30:00.123456\") AS timestamp," +
                    "  ST_GEOGPOINT(50.6833, 2.9) AS geopoint," +
                    "  ARRAY(SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS `array`," +
                    "  STRUCT(4 AS x, 0 AS y, ARRAY(SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS z) AS `struct`",
                "flow", ImmutableMap.of("id", FriendlyId.createFriendlyId(), "namespace", "org.kestra.tests"),
                "execution", ImmutableMap.of("id", FriendlyId.createFriendlyId()),
                "taskrun", ImmutableMap.of("id", FriendlyId.createFriendlyId())
            )
        );

        Query task = Query.builder()
            .sql("{{sql}}")
            .fetch(true)
            .build();

        RunOutput run = task.run(runContext);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) run.getOutputs().get("rows");
        assertThat(rows.size(), is(1));

        assertThat(rows.get(0).get("string"), is("hello"));
        assertThat(rows.get(0).get("nullable"), is(nullValue()));
        assertThat(rows.get(0).get("int"), is(1L));
        assertThat(rows.get(0).get("float"), is(1.25D));
        assertThat(rows.get(0).get("date"), is(LocalDate.parse("2008-12-25")));
        assertThat(rows.get(0).get("time"), is(LocalTime.parse("15:30:00.123456")));
        assertThat(rows.get(0).get("timestamp"), is(Instant.parse("2008-12-25T15:30:00.123Z")));
        assertThat((List<Double>) rows.get(0).get("geopoint"), containsInAnyOrder(50.6833, 2.9));
        assertThat((List<Long>) rows.get(0).get("array"), containsInAnyOrder(1L, 2L, 3L));
        assertThat(((Map<String, Object>) rows.get(0).get("struct")).get("x"), is(4L));
        assertThat(((Map<String, Object>) rows.get(0).get("struct")).get("y"), is(0L));
        assertThat((List<Long>) ((Map<String, Object>) rows.get(0).get("struct")).get("z"), containsInAnyOrder(1L, 2L, 3L));
    }

    @Test
    void destination() throws Exception {
        Query task = Query.builder()
            .id(QueryTest.class.getSimpleName())
            .type(Query.class.getName())
            .sql("{{#each inputs.loop}}" +
                "SELECT" +
                "  \"{{execution.id}}\" as execution_id," +
                "  TIMESTAMP \"{{instantFormat execution.startDate \"yyyy-MM-dd HH:mm:ss.SSSSSS\"}}\" as execution_date," +
                "  {{@key}} as counter" +
                "{{#unless @last}}\nUNION ALL\n{{/unless}}" +
                "{{/each}}"
            )
            .destinationTable(project + "." + dataset + "." + FriendlyId.createFriendlyId())
            .timePartitioningField("execution_date")
            .clusteringFields(Arrays.asList("execution_id", "counter"))
            .schemaUpdateOptions(Collections.singletonList(JobInfo.SchemaUpdateOption.ALLOW_FIELD_ADDITION))
            .writeDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
            .build();

        RunContext runContext = Utils.mockRunContext(applicationContext, task, ImmutableMap.of(
            "loop", ContiguousSet.create(Range.closed(1, 25), DiscreteDomain.integers())
        ));

        RunOutput run = task.run(runContext);
    }
}