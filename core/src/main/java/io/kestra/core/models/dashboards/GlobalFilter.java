package io.kestra.core.models.dashboards;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class GlobalFilter {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private Integer pageSize;
    private Integer pageNumber;
    private String namespace;
    private Map<String, String> labels;
}