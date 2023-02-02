package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@Slf4j
public class FlowWithSource extends Flow {
    String source;

    public Flow toFlow() {
        return Flow.builder()
            .id(this.id)
            .namespace(this.namespace)
            .revision(this.revision)
            .description(this.description)
            .labels(this.labels)
            .inputs(this.inputs)
            .variables(this.variables)
            .tasks(this.tasks)
            .errors(this.errors)
            .listeners(this.listeners)
            .triggers(this.triggers)
            .taskDefaults(this.taskDefaults)
            .disabled(this.disabled)
            .deleted(this.deleted)
            .build();
    }

    public String getSource() {
        String source = this.source;

        // previously, we insert source on database keeping default value (like deleted, ...)
        // if the previous serialization is the same as actual one, we use a clean version removing them
        try {
            Flow flow = toFlow();

            if (source == null) {
                return toYamlWithoutDefault(flow);
            }

            if (JacksonMapper.ofYaml().writeValueAsString(flow).equals(source)) {
                source = toYamlWithoutDefault(flow);
            }
        } catch (JsonProcessingException e) {
            log.warn("Unable to convert flow json '{}' '{}'({})", this.getNamespace(), this.getId(), this.getRevision(), e);
        }

        // same here but with version that don't make any sense on the source code, so removing it
        return cleanupSource(source);
    }

    private static String cleanupSource(String source) {
        return source.replaceFirst("(?m)^revision: \\d+\n?","");
    }

    public boolean isUpdatable(Flow flow, String flowSource) {
        return flow.equalsWithoutRevision(flow) &&
            this.source.equals(cleanupSource(flowSource));
    }

    public static FlowWithSource of(Flow flow, String source) {
        return FlowWithSource.builder()
            .id(flow.id)
            .namespace(flow.namespace)
            .revision(flow.revision)
            .description(flow.description)
            .labels(flow.labels)
            .inputs(flow.inputs)
            .variables(flow.variables)
            .tasks(flow.tasks)
            .errors(flow.errors)
            .listeners(flow.listeners)
            .triggers(flow.triggers)
            .taskDefaults(flow.taskDefaults)
            .disabled(flow.disabled)
            .deleted(flow.deleted)
            .source(source)
            .build();
    }

    private static String toYamlWithoutDefault(Object Object) throws JsonProcessingException {
        return JacksonMapper.ofYaml()
            .writeValueAsString(
                JacksonMapper
                    .ofJson()
                    .readTree(
                        JacksonMapper
                            .ofJson()
                            .copy()
                            .setSerializationInclusion(
                                JsonInclude.Include.NON_DEFAULT)
                            .writeValueAsString(Object)
                    )
            );
    }
}
