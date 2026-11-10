package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.repositories.PaginationType;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.data.model.Page;

final class LogToolPages {
    static final int DEFAULT_MAX_MESSAGE_LENGTH = 2000;

    private LogToolPages() {
    }

    static int maxMessageLength(final Integer maxMessageLength) {
        if (maxMessageLength != null && maxMessageLength < 1) {
            throw new IllegalArgumentException("maxMessageLength must be 1 or greater, got %d.".formatted(maxMessageLength));
        }
        return maxMessageLength == null ? DEFAULT_MAX_MESSAGE_LENGTH : maxMessageLength;
    }

    static Page<LogEntry> find(final LogDataStoreInterface logRepository, final String tenant, final List<QueryFilter> filters,
        final int page, final int size, final String sort) {
        return logRepository.find(PageableUtils.from(page, size, List.of(sort)), tenant, filters);
    }

    static Long total(final LogDataStoreInterface logRepository, final Page<LogEntry> page) {
        return logRepository.paginationType() == PaginationType.CURSOR ? null : page.getTotalSize();
    }

    static String truncate(final String message, final int maxMessageLength) {
        if (message == null || message.length() <= maxMessageLength) {
            return message;
        }
        return message.substring(0, maxMessageLength) + "…";
    }
}
