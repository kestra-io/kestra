package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    @SneakyThrows
    private static String toYamlWithoutDefault(Object object) throws JsonProcessingException {
        String json = JacksonMapper
            .ofJson()
            .copy()
            .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
            .writeValueAsString(object);

        Object map = fixSnakeYaml(JacksonMapper.toMap(json));

        return JacksonMapper.ofYaml().writeValueAsString(map);
    }

    /**
     * Dirty hack but only concern previous flow with no source code in org.yaml.snakeyaml.emitter.Emitter:
     * <pre>
     * if (previousSpace) {
     *   spaceBreak = true;
     * }
     * </pre>
     * This control will detect ` \n` as a no valid entry on a string and will break the multiline to transform in single line
     *
     * @param object the object to fix
     * @return the modified object
     */
    private static Object fixSnakeYaml(Object object) {
        if (object instanceof Map) {
            return ((Map<?, ?>) object)
                .entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                    fixSnakeYaml(entry.getKey()),
                    fixSnakeYaml(entry.getValue())
                ))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (u, v) -> {
                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new
                ));
        } else if (object instanceof Collection) {
            return ((Collection<?>) object)
                .stream()
                .map(FlowWithSource::fixSnakeYaml)
                .collect(Collectors.toList());
        } else if (object instanceof String) {
            String item = (String) object;

            if (item.contains("\n")) {
                return item.replaceAll("\\s+\\n", "\\\n");
            }
        }

        return object;
    }
}
