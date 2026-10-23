package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.PebbleUtil;
import io.kestra.core.validations.TableInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@EeOnly
@TableInputValidation
public class TableInput extends Input<List<?>> implements RenderableInput {
    @Schema(
        title = "The typed columns of the table.",
        description = "Each column is a regular input declaration (e.g. `INT` with `min`/`max`, `STRING` with a `validator`) " +
            "applied to the corresponding key of every row. The value is a list of objects, referenced as " +
            "`{{ inputs.myTable[0].myColumn }}`. Columns cannot be of type `TABLE`, `ARRAY`, `FORM`, `FILE`, `SECRET`, " +
            "`JSON`, `ION` or `YAML`, and cannot declare `defaults` or `dependsOn`. A column's `prefill` fills the new rows, " +
            "and a `SELECT` or `MULTISELECT` column can take its values from an `expression`, rendered with the inputs listed " +
            "in the table's `dependsOn`. This input type is only available in the Enterprise Edition."
    )
    @NotNull
    @Valid
    private List<Input<?>> columns;

    @Schema(title = "Minimum number of rows.")
    @Min(0)
    private Integer minRows;

    @Schema(title = "Maximum number of rows.")
    @Min(1)
    private Integer maxRows;

    @Schema(
        title = "The conditions a row of `defaults` must match to be shown and passed to the execution.",
        description = "Only the rows matching every filter are kept, and no row can be added. The filters are rendered again " +
            "whenever an input listed in `dependsOn` changes."
    )
    private List<@Valid TableFilter> filters;

    @Schema(
        title = "Whether a row kept by `filters` can be duplicated.",
        description = "A duplicated row can be deleted again, while a row from `defaults` cannot. Without `filters`, rows can always be added and duplicated."
    )
    @Builder.Default
    private Boolean allowRowDuplication = false;

    public List<?> matchingRows(List<?> rows) {
        if (ListUtils.isEmpty(filters)) {
            return rows;
        }
        return rows.stream()
            .filter(row -> !(row instanceof Map<?, ?> cells) || filters.stream().allMatch(filter -> filter.matches(cells)))
            .toList();
    }

    @Override
    public Input<?> render(Function<String, Object> renderer) {
        List<Input<?>> declared = ListUtils.emptyOnNull(columns);
        List<Input<?>> renderedColumns = declared.stream().<Input<?>>map(column -> RenderableInput.mayRenderInput(column, renderer)).toList();
        boolean columnsRendered = IntStream.range(0, declared.size()).anyMatch(index -> renderedColumns.get(index) != declared.get(index));
        boolean filtersRendered = ListUtils.emptyOnNull(filters).stream().anyMatch(filter -> filter.value() instanceof String text && PebbleUtil.containsOpeningBlockDelimiter(text));
        if (!columnsRendered && !filtersRendered) {
            return this;
        }
        Map<String, Object> rendered = JacksonMapper.toMap(this);
        if (columnsRendered) {
            rendered.put("columns", renderedColumns);
        }
        if (filtersRendered) {
            rendered.put("filters", filters.stream().map(filter -> new TableFilter(filter.column(), filter.operation(), renderedValue(filter, renderer))).toList());
        }
        return JacksonMapper.toMap(rendered, TableInput.class);
    }

    private Object renderedValue(TableFilter filter, Function<String, Object> renderer) {
        if (!(filter.value() instanceof String text) || !PebbleUtil.containsOpeningBlockDelimiter(text)) {
            return filter.value();
        }
        try {
            return renderer.apply(text.trim());
        } catch (Exception e) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "Cannot render the value of the filter on `" + filter.column() + "`. Cause: " + e.getMessage(),
                this,
                TableInput.class,
                getId(),
                this
            );
        }
    }

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        if (minRows != null && input.size() < minRows) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at least `" + minRows + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }

        if (maxRows != null && input.size() > maxRows) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at most `" + maxRows + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }
    }
}
