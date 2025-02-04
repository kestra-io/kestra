package io.kestra.plugin.core.log;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class PurgeLogsTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private LogRepositoryInterface logRepository;

    @Test
    void run() throws Exception {
        // create an execution to delete
        var logEntry = LogEntry.builder()
            .namespace("namespace")
            .flowId("flowId")
            .timestamp(Instant.now())
            .level(Level.INFO)
            .message("Hello World")
            .build();
        logRepository.save(logEntry);

        var purge = PurgeLogs.builder()
            .endDate(Property.of(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var runContext = runContextFactory.of(Map.of("flow", Map.of("namespace", "namespace", "id", "flowId")));
        var output = purge.run(runContext);

        assertThat(output.getCount(), is(1));
    }
}