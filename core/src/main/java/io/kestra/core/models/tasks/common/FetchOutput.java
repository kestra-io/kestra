package io.kestra.core.models.tasks.common;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * This output can be used as a result of a task that fetched data.
 * It is designed to be used in conjunction with a <code>fetchType</code> plugin property of type {@link FetchType}.
 */
@Builder
@Getter
public class FetchOutput implements Output {
    @Schema(
        title = "List containing the fetched data",
        description = "Only populated if using `fetchType=FETCH`."
    )
    private List<Object> rows;

    @Schema(
        title = "Map containing the first row of fetched data",
        description = "Only populated if using `fetchType=FETCH_ONE`."
    )
    private Map<String, Object> row;

    @Schema(
        title = "The uri of stored data",
        description = "Only populated if using `fetchType=STORE`"
    )
    private URI uri;

    @Schema(
        title = "The size of the fetched rows"
    )
    private Long size;
}
