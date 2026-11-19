package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

/**
 * Paging helpers shared by the agent tools that list or search, so every one reads a 1-based {@code page} the same way.
 */
final class ToolPages {
    private ToolPages() {
    }

    /**
     * @param page the page asked for, or {@code null} for the first one
     * @throws IllegalArgumentException if the page is lower than 1
     */
    static int pageNumber(final Integer page) {
        if (page != null && page < 1) {
            throw new IllegalArgumentException("Page must be 1 or greater, got %d.".formatted(page));
        }
        return page == null ? 1 : page;
    }

    static <T> List<T> slice(final List<T> items, final int page, final int size) {
        return items.stream().skip((long) (page - 1) * size).limit(size).toList();
    }
}
