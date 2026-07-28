package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.repositories.PaginationType;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.CursorOrOffsetPagedResults;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller-level test of the cursor pagination path: {@code GET /logs/search} against a cursor-paginated
 * store must serialize {@code {results, type:"CURSOR", nextCursor}} with no {@code total}, and a {@code ?cursor=}
 * param must be handed to the store verbatim as the {@link Pageable} cursor. The store is a Mockito mock (the real
 * offset path is covered by {@code LogControllerTest}).
 */
@KestraTest
class LogControllerCursorTest {

    @Inject
    LogDataStoreInterface logRepository;

    @Inject
    TenantService tenantService;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @MockBean(LogDataStoreInterface.class)
    LogDataStoreInterface logRepository() {
        return mock(LogDataStoreInterface.class);
    }

    @MockBean(TenantService.class)
    TenantService tenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    TenantValidationFilter tenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    private static LogEntry logEntry() {
        return LogEntry.builder()
            .flowId(IdUtils.create()).namespace("io.kestra.unittest").taskId("taskId")
            .executionId(IdUtils.create()).taskRunId(IdUtils.create()).attemptNumber(0)
            .timestamp(Instant.now()).level(Level.INFO).thread("").message("john doe")
            .build();
    }

    private static Page<LogEntry> cursorPage(String nextToken) {
        return CursoredPage.of(
            List.of(logEntry(), logEntry()),
            Pageable.from(1, 10),
            List.of(Pageable.Cursor.of("first-token"), Pageable.Cursor.of(nextToken)),
            null // no total → cursor mode
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorResponseHasTypeAndNextCursorButNoTotal() {
        reset(logRepository);
        when(tenantService.resolveTenant()).thenReturn("main");
        when(logRepository.find(any(), any(), any())).thenReturn(cursorPage("next-token"));

        CursorOrOffsetPagedResults<LogEntry> res = client.toBlocking().retrieve(
            GET("/api/v1/main/logs/search"),
            Argument.of(CursorOrOffsetPagedResults.class, LogEntry.class)
        );

        assertThat(res.getResults()).hasSize(2);
        assertThat(res.getType()).isEqualTo(PaginationType.CURSOR);
        assertThat(res.getNextCursor()).isEqualTo("next-token");
        assertThat(res.getTotal()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorParamIsPassedToTheStoreVerbatim() {
        reset(logRepository);
        when(tenantService.resolveTenant()).thenReturn("main");
        when(logRepository.find(any(), any(), any())).thenReturn(cursorPage("next-token"));

        client.toBlocking().retrieve(
            GET("/api/v1/main/logs/search?cursor=opaque-token-123"),
            Argument.of(CursorOrOffsetPagedResults.class, LogEntry.class)
        );

        var captor = forClass(Pageable.class);
        verify(logRepository).find(captor.capture(), any(), any());
        assertThat(captor.getValue().cursor()).isPresent();
        assertThat(captor.getValue().cursor().get().get(0)).isEqualTo("opaque-token-123");
    }
}
