package io.kestra.core.http.client.configurations;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import lombok.experimental.SuperBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BasicAuthConfiguration.class, name = "BASIC"),
    @JsonSubTypes.Type(value = BearerAuthConfiguration.class, name = "BEARER")
})
@SuperBuilder(toBuilder = true)
public abstract class AbstractAuthConfiguration {
    public abstract Property<AuthType> getType();

    public abstract void configure(HttpClientBuilder builder, RunContext runContext) throws IllegalVariableEvaluationException;

    public enum AuthType {
        BASIC,
        BEARER
    }
}
