package io.kestra.core.models.dashboards.filters;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Contains.class, name = "CONTAINS"),
    @JsonSubTypes.Type(value = EndsWith.class, name = "ENDS_WITH"),
    @JsonSubTypes.Type(value = EqualTo.class, name = "EQUAL_TO"),
    @JsonSubTypes.Type(value = GreaterThan.class, name = "GREATER_THAN"),
    @JsonSubTypes.Type(value = GreaterThanOrEqualTo.class, name = "GREATER_THAN_OR_EQUAL_TO"),
    @JsonSubTypes.Type(value = In.class, name = "IN"),
    @JsonSubTypes.Type(value = IsFalse.class, name = "IS_FALSE"),
    @JsonSubTypes.Type(value = IsNotNull.class, name = "IS_NOT_NULL"),
    @JsonSubTypes.Type(value = IsNull.class, name = "IS_NULL"),
    @JsonSubTypes.Type(value = IsTrue.class, name = "IS_TRUE"),
    @JsonSubTypes.Type(value = LessThan.class, name = "LESS_THAN"),
    @JsonSubTypes.Type(value = LessThanOrEqualTo.class, name = "LESS_THAN_OR_EQUAL_TO"),
    @JsonSubTypes.Type(value = NotEqualTo.class, name = "NOT_EQUAL_TO"),
    @JsonSubTypes.Type(value = NotIn.class, name = "NOT_IN"),
    @JsonSubTypes.Type(value = Or.class, name = "OR"),
    @JsonSubTypes.Type(value = Regex.class, name = "REGEX"),
    @JsonSubTypes.Type(value = StartsWith.class, name = "STARTS_WITH"),
})
@Getter
@NoArgsConstructor
@SuperBuilder
@Introspected
public abstract class AbstractFilter<F extends Enum<F>> {
    private F field;
    private String labelKey;

    abstract public String getType();

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof AbstractFilter<?> filter) {
            return filter.getType().equals(this.getType());
        }

        return false;
    }
}
