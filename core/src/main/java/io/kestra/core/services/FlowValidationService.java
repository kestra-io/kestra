package io.kestra.core.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.ClassUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Pause;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FlowValidationService {
    @Inject
    private PluginRegistry pluginRegistry;

    @Inject
    private Optional<FlowRepositoryInterface> flowRepository;

    public List<String> deprecationPaths(Flow flow) {
        return deprecationTraversal("", flow).toList();
    }

    public List<String> warnings(Flow flow, String tenantId) {
        if (flow == null) {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<>(checkValidSubflows(flow, tenantId));

        List<io.kestra.plugin.core.trigger.Flow> flowTriggers = ListUtils.emptyOnNull(flow.getTriggers()).stream()
            .filter(io.kestra.plugin.core.trigger.Flow.class::isInstance)
            .map(io.kestra.plugin.core.trigger.Flow.class::cast)
            .toList();
        flowTriggers.forEach(flowTrigger ->
        {
            if (ListUtils.emptyOnNull(flowTrigger.getConditions()).isEmpty() && flowTrigger.getPreconditions() == null) {
                warnings.add(
                    "This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger '" + flowTrigger.getId()
                        + "'."
                );
            }
        });

        // add warning for runnable properties (timeout, workerGroup, taskCache) when used not in a runnable
        flow.allTasksWithChilds().forEach(task ->
        {
            if (!(task instanceof RunnableTask<?>)) {
                if (task.getTimeout() != null && !(task instanceof Pause)) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'timeout' property as it's only relevant for runnable tasks.");
                }
                if (task.getTaskCache() != null) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'taskCache' property as it's only relevant for runnable tasks.");
                }
                if (task.getWorkerGroup() != null) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'workerGroup' property as it's only relevant for runnable tasks.");
                }
            }
        });

        return warnings;
    }

    public List<Relocation> relocations(String flowSource) {
        try {
            Map<String, Class<?>> aliases = pluginRegistry.plugins().stream()
                .flatMap(plugin -> plugin.getAliases().values().stream())
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, duplicate) -> existing
                    )
                );
            Map<String, Object> stringObjectMap = JacksonMapper.ofYaml().readValue(flowSource, JacksonMapper.MAP_TYPE_REFERENCE);
            return relocations(aliases, stringObjectMap);
        } catch (JsonProcessingException e) {
            // silent failure (we don't compromise the app / response for warnings)
            return Collections.emptyList();
        }
    }

    public record Relocation(String from, String to) {
    }

    public List<String> checkValidSubflows(Flow flow, String tenantId) {
        List<io.kestra.plugin.core.flow.Subflow> subFlows = ListUtils.emptyOnNull(flow.getTasks()).stream()
            .filter(io.kestra.plugin.core.flow.Subflow.class::isInstance)
            .map(io.kestra.plugin.core.flow.Subflow.class::cast)
            .toList();

        List<String> violations = new ArrayList<>();

        subFlows.forEach(subflow ->
        {
            String regex = ".*\\{\\{.+}}.*"; // regex to check if string contains pebble
            String subflowId = subflow.getFlowId();
            String namespace = subflow.getNamespace();
            if ((subflowId != null && subflowId.matches(regex)) || (namespace != null && namespace.matches(regex))) {
                return;
            }
            if (subflowId == null || namespace == null) {
                // those fields are mandatory so the mandatory validation will apply
                return;
            }
            Optional<Flow> optional = findById(tenantId, subflow.getNamespace(), subflow.getFlowId());

            if (optional.isEmpty()) {
                violations.add("The subflow '" + subflow.getFlowId() + "' not found in namespace '" + subflow.getNamespace() + "'.");
            } else if (optional.get().isDisabled()) {
                violations.add("The subflow '" + subflow.getFlowId() + "' is disabled in namespace '" + subflow.getNamespace() + "'.");
            }
        });

        return violations;
    }

    @SuppressWarnings("unchecked")
    private List<Relocation> relocations(Map<String, Class<?>> aliases, Map<String, Object> stringObjectMap) {
        List<Relocation> relocations = new ArrayList<>();
        for (Map.Entry<String, Object> entry : stringObjectMap.entrySet()) {
            if (entry.getValue() instanceof String value && aliases.containsKey(value)) {
                relocations.add(new Relocation(value, aliases.get(value).getName()));
            }

            if (entry.getValue() instanceof Map<?, ?> value) {
                relocations.addAll(relocations(aliases, (Map<String, Object>) value));
            }

            if (entry.getValue() instanceof List<?> value) {
                List<Relocation> listAliases = value.stream().flatMap(item ->
                {
                    if (item instanceof Map<?, ?> map) {
                        return relocations(aliases, (Map<String, Object>) map).stream();
                    }
                    return Stream.empty();
                }).toList();
                relocations.addAll(listAliases);
            }
        }

        return relocations;
    }

    private Stream<String> deprecationTraversal(String prefix, Object object) {
        if (object == null || ClassUtils.isPrimitiveOrWrapper(object.getClass()) || String.class.equals(object.getClass())) {
            return Stream.empty();
        }

        return Stream.concat(
            object.getClass().isAnnotationPresent(Deprecated.class) ? Stream.of(prefix) : Stream.empty(),
            allGetters(object.getClass())
                .flatMap(method ->
                {
                    try {
                        Object fieldValue = method.invoke(object);

                        if (fieldValue instanceof Iterable<?> iterableValue) {
                            fieldValue = StreamSupport.stream(iterableValue.spliterator(), false).toArray(Object[]::new);
                        }

                        String fieldName = method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
                        Stream<String> additionalDeprecationPaths = Stream.empty();
                        if (fieldValue instanceof Object[] arrayValue) {
                            additionalDeprecationPaths = IntStream.range(0, arrayValue.length).boxed().flatMap(i -> deprecationTraversal(fieldName + "[%d]".formatted(i), arrayValue[i]));
                        }

                        return Stream.concat(
                            method.isAnnotationPresent(Deprecated.class) && fieldValue != null ? Stream.of(prefix.isEmpty() ? fieldName : prefix + "." + fieldName) : Stream.empty(),
                            additionalDeprecationPaths
                        );
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        // silent failure (we don't compromise the app / response for warnings)
                    }

                    return Stream.empty();
                })
        );
    }

    private Stream<Method> allGetters(Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
            .filter(m -> !m.getDeclaringClass().equals(Object.class))
            .filter(method -> method.getName().startsWith("get") && method.getName().length() > 3 && method.getParameterCount() == 0)
            .filter(method -> !method.getReturnType().equals(Void.TYPE))
            .filter(method -> !Modifier.isStatic(method.getModifiers()));
    }

    private Optional<Flow> findById(String tenantId, String namespace, String flowId) {
        if (flowRepository.isEmpty()) {
            return Optional.empty();
        }

        return flowRepository.get().findById(tenantId, namespace, flowId);
    }
}
