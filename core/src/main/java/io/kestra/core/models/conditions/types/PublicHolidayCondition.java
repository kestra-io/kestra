package io.kestra.core.models.conditions.types;

import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.Locale;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events on public holidays."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Condition to allow events on public holidays.",
            code = {
                """
                - conditions:
                    - type: io.kestra.core.models.conditions.types.PublicHolidayCondition
                      country: FR
                """
            }
        ),
        @Example(
            full = true,
            title = "Conditions to allow events on work days.",
            code = {
                """
                - conditions:
                    - type: io.kestra.core.models.conditions.types.NotCondition
                      conditions:
                        - type: io.kestra.core.models.conditions.types.PublicHolidayCondition
                          country: FR
                        - type: io.kestra.core.models.conditions.types.WeekendCondition
                """
            }
        )
    }
)
public class PublicHolidayCondition extends Condition implements ScheduleCondition {
    @NotEmpty
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private String date = "{{ trigger.date }}";

    @Schema(
        title = "[ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) country code. If not set, it uses the country code from the default locale.",
        description = "It uses the [Jollyday](https://github.com/focus-shift/jollyday) library for public holiday calendar that supports more than 70 countries."
    )
    @PluginProperty(dynamic = true)
    private String country;

    @Schema(
        title = "[ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) country subdivision (e.g., provinces and states) code.",
        description = "It uses the [Jollyday](https://github.com/focus-shift/jollyday) library for public holiday calendar that supports more than 70 countries."
    )
    @PluginProperty(dynamic = true)
    private String subDivision;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        var renderedCountry = conditionContext.getRunContext().render(this.country);
        var renderedSubDivision = conditionContext.getRunContext().render(this.subDivision);

        HolidayManager holidayManager = renderedCountry != null ? HolidayManager.getInstance(ManagerParameters.create(renderedCountry)) : HolidayManager.getInstance();
        LocalDate currentDate = DateUtils.parseLocalDate(conditionContext.getRunContext().render(date));
        return renderedSubDivision == null ? holidayManager.isHoliday(currentDate) : holidayManager.isHoliday(currentDate, renderedSubDivision);
    }
}
