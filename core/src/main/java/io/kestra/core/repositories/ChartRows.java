package io.kestra.core.repositories;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.dashboards.ChartMetadata;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * The rows of a chart, with the metadata of the data when the data source has some.
 */
public class ChartRows extends ArrayListTotal<Map<String, Object>> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ChartMetadata metadata;

    public ChartRows(List<Map<String, Object>> rows, long total, @Nullable ChartMetadata metadata) {
        super(rows, total);
        this.metadata = metadata;
    }

    /** All the rows when there is no pageable, otherwise the requested page. */
    public static ChartRows of(@Nullable Pageable pageable, List<Map<String, Object>> rows, @Nullable ChartMetadata metadata) {
        if (pageable == null) {
            return new ChartRows(rows, rows.size(), metadata);
        }
        ArrayListTotal<Map<String, Object>> page = ArrayListTotal.of(pageable, rows);
        return new ChartRows(page, page.getTotal(), metadata);
    }

    @Nullable
    public ChartMetadata getMetadata() {
        return metadata;
    }
}
