package io.kestra.plugin.core.condition;

import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Map;

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
            title = "Trigger condition to execute the flow only on public holidays.",
            code = """
                id: schedule_condition_public-holiday
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on the public holidays of France at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.PublicHoliday
                        country: FR
                """
        ),
        @Example(
            full = true,
            title = "Trigger condition to execute the flow only on work days in France.",
            code = """
                id: schedule-condition-work-days
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on the work days of France at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.Not
                        conditions:
                          - type: io.kestra.plugin.core.condition.PublicHoliday
                            country: FR
                          - type: io.kestra.plugin.core.condition.Weekend
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.PublicHolidayCondition", "io.kestra.plugin.core.condition.PublicHolidayCondition"}
)
public class PublicHoliday extends Condition implements ScheduleCondition {
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @NotNull
    @Builder.Default
    private Property<String> date = Property.ofExpression("{{ trigger.date}}");

    @Schema(
        title = "[ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) country code. If not set, it uses the country code from the default locale.",
        description = "It uses the [Jollyday](https://github.com/focus-shift/jollyday) library for public holiday calendar that supports more than 70 countries."
    )
    private Property<String> country;

    @Schema(
        title = "[ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) country subdivision (e.g., provinces and states) code.",
        description = "It uses the [Jollyday](https://github.com/focus-shift/jollyday) library for public holiday calendar that supports more than 70 countries."
    )
    private Property<String> subDivision;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        Map<String, Object> variables=conditionContext.getVariables();
        var renderedCountry = conditionContext.getRunContext().render(this.country).as(String.class).orElse(null);
        var renderedSubDivision = conditionContext.getRunContext().render(this.subDivision).as(String.class).orElse(null);

        HolidayManager holidayManager = renderedCountry != null ? HolidayManager.getInstance(ManagerParameters.create(renderedCountry)) : HolidayManager.getInstance();
        LocalDate currentDate = DateUtils.parseLocalDate(conditionContext.getRunContext().render(date).as(String.class,variables).orElseThrow());
        return renderedSubDivision == null ? holidayManager.isHoliday(currentDate) : holidayManager.isHoliday(currentDate, renderedSubDivision);
    }
}
