package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for allows events on weekend"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.WeekendCondition",
            }
        )
    }
)
public class WeekendCondition extends Condition {
    @NotNull
    @Schema(
        title = "The date to test",
        description = "Can be any variable or any valid ISO 8601 datetime, default will use `{{ now \"iso_local_date\" }}`"
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    public String date = "{{ now \"iso_local_date\" }}";

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date);
        LocalDate currentDate = DateUtils.parseLocalDate(render);

        return currentDate.getDayOfWeek().equals(DayOfWeek.SATURDAY) ||
            currentDate.getDayOfWeek().equals(DayOfWeek.SUNDAY);
    }
}
