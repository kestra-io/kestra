package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides business logic to manipulate {@link Flow}
 */
@Singleton
@Slf4j
public class FlowService {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ConditionService conditionService;

    @Inject
    ApplicationContext applicationContext;

    public Stream<Flow> keepLastVersion(Stream<Flow> stream) {
        return keepLastVersionCollector(stream);
    }

    public Flow keepLastVersion(Stream<Flow> stream, String namespace, String flowId) {
        return keepLastVersionCollector(
            stream
                .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(flowId))
        )
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + flowId + "'"));
    }

    public Collection<Flow> keepLastVersion(List<Flow> flows) {
        return keepLastVersionCollector(flows.stream())
            .collect(Collectors.toList());
    }

    private Stream<Flow> keepLastVersionCollector(Stream<Flow> stream) {
        return stream
            .sorted((left, right) -> left.getRevision() > right.getRevision() ? -1 : (left.getRevision().equals(right.getRevision()) ? 0 : 1))
            .collect(Collectors.groupingBy(Flow::uidWithoutRevision))
            .values()
            .stream()
            .map(flows -> {
                Flow flow = flows.stream().findFirst().orElseThrow();

                // edge case, 2 flows with same revision, we keep the deleted
                final Flow finalFlow = flow;
                Optional<Flow> deleted = flows.stream()
                    .filter(f -> f.getRevision().equals(finalFlow.getRevision()) && f.isDeleted())
                    .findFirst();

                if (deleted.isPresent()) {
                    return null;
                }

                return flow.isDeleted() ? null : flow;
            })
            .filter(Objects::nonNull);
    }

    protected boolean removeUnwanted(Flow f, Execution execution) {
        // we don't allow recursive
        return !f.uidWithoutRevision().equals(Flow.uidWithoutRevision(execution));
    }

    public static List<AbstractTrigger> findRemovedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(previous.getTriggers())
            .stream()
            .filter(p -> ListUtils.emptyOnNull(flow.getTriggers())
                .stream()
                .noneMatch(c -> c.getId().equals(p.getId()))
            )
            .collect(Collectors.toList());
    }

    public static String cleanupSource(String source) {
        return source.replaceFirst("(?m)^revision: \\d+\n?", "");
    }

    public static String injectDisabled(String source, Boolean disabled) {
        String regex = disabled ? "^disabled\\s*:\\s*false\\s*" : "^disabled\\s*:\\s*true\\s*";

        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        if (p.matcher(source).find()) {
            return p.matcher(source).replaceAll(String.format("disabled: %s\n", disabled));
        }

        return source + String.format("\ndisabled: %s", disabled);
    }

    public static String generateSource(Flow flow, @Nullable String source) {
        try {
            if (source == null) {
                return toYamlWithoutDefault(flow);
            }

            if (JacksonMapper.ofYaml().writeValueAsString(flow).equals(source)) {
                source = toYamlWithoutDefault(flow);
            }
        } catch (JsonProcessingException e) {
            log.warn("Unable to convert flow json '{}' '{}'({})", flow.getNamespace(), flow.getId(), flow.getRevision(), e);
        }

        return source;
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
                .map(FlowService::fixSnakeYaml)
                .collect(Collectors.toList());
        } else if (object instanceof String) {
            String item = (String) object;

            if (item.contains("\n")) {
                return item.replaceAll("\\s+\\n", "\\\n");
            }
        }

        return object;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class FlowWithFlowTrigger {
        private final Flow flow;
        private final io.kestra.core.models.triggers.types.Flow trigger;
    }
}
