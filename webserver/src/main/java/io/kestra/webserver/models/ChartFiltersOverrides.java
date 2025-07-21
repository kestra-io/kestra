package io.kestra.webserver.models;

import io.kestra.core.models.QueryFilter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ChartFiltersOverrides {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private Integer pageSize;
    private Integer pageNumber;
    private String namespace;
    private Map<String, String> labels;
    private List<QueryFilter> filters = new ArrayList<>();
}
