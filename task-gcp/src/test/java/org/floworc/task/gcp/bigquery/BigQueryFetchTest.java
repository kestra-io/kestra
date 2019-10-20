package org.floworc.task.gcp.bigquery;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@MicronautTest
class BigQueryFetchTest {
    @Inject
    private StorageInterface storageInterface;

    @Test
    @SuppressWarnings("unchecked")
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.storageInterface,
            ImmutableMap.of(
                "sql", "SELECT " +
                    "  \"hello\" as string," +
                    "  1 as int," +
                    "  1.25 AS float," +
                    "  DATE(\"2008-12-25\") AS date," +
                    "  DATETIME \"2008-12-25 15:30:00.123456\" AS datetime," +
                    "  TIME(DATETIME \"2008-12-25 15:30:00.123456\") AS time," +
                    "  TIMESTAMP(\"2008-12-25 15:30:00.123456\") AS timestamp," +
                    "  ST_GEOGPOINT(50.6833, 2.9) AS geopoint," +
                    "  ARRAY(SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS `array`," +
                    "  STRUCT(4 AS x, 0 AS y, ARRAY(SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS z) AS `struct`"
            )
        );

        BigQueryFetch task = BigQueryFetch.builder()
            .sql("{{sql}}")
            .build();

        RunOutput run = task.run(runContext);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) run.getOutputs().get("rows");
        assertThat(rows.size(), is(1));

        assertThat(rows.get(0).get("string"), is("hello"));
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
}