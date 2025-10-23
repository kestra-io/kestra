package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasSource;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.Label;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.serializers.JacksonMapper;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The base interface for FLow.
 */
@JsonDeserialize(as = GenericFlow.class)
public interface FlowInterface extends FlowId, DeletedInterface, TenantInterface, HasUID, HasSource {

    Pattern YAML_REVISION_MATCHER = Pattern.compile("(?m)^revision: \\d+\n?");

    String getDescription();

    boolean isDisabled();

    boolean isDeleted();

    List<Label> getLabels();

    List<Input<?>> getInputs();

    List<Output> getOutputs();

    Map<String, Object> getVariables();

    WorkerGroup getWorkerGroup();

    default Concurrency getConcurrency() {
        return null;
    }

    default List<SLA> getSla() {
        return List.of();
    }

    String getSource();

    @Override
    @JsonIgnore
    default String source() {
        return getSource();
    }

    @Override
    @JsonIgnore
    default String uid() {
        return FlowId.uid(this);
    }

    @JsonIgnore
    default String uidWithoutRevision() {
        return FlowId.uidWithoutRevision(this);
    }

    /**
     * Checks whether this flow is equals to the given flow.
     * <p>
     * This method is used to compare if two flow revisions are equal.
     *
     * @param flow  The flow to compare.
     * @return {@code true} if both flows are the same. Otherwise {@code false}
     */
    @JsonIgnore
    default boolean isSameWithSource(final FlowInterface flow) {
        return
            Objects.equals(this.uidWithoutRevision(), flow.uidWithoutRevision()) &&
                Objects.equals(this.isDeleted(), flow.isDeleted()) &&
                Objects.equals(this.isDisabled(), flow.isDisabled()) &&
                Objects.equals(sourceWithoutRevision(this.getSource()), sourceWithoutRevision(flow.getSource()));
    }

    /**
     * Checks whether this flow matches the given {@link FlowId}.
     *
     * @param that  The {@link FlowId}.
     * @return {@code true} if the passed id matches this flow.
     */
    @JsonIgnore
    default boolean isSameId(FlowId that) {
        if (that == null) return false;
        return
            Objects.equals(this.getTenantId(), that.getTenantId()) &&
            Objects.equals(this.getNamespace(), that.getNamespace()) &&
            Objects.equals(this.getId(), that.getId());
    }

    /**
     * Static method for removing the 'revision' field from a flow.
     *
     * @param source    The source.
     * @return  The source without revision.
     */
    static String sourceWithoutRevision(final String source) {
        return YAML_REVISION_MATCHER.matcher(source).replaceFirst("");
    }

    /**
     * Returns the source code for this flow or generate one if {@code null}.
     * <p>
     * This method must only be used for testing purpose or for handling backward-compatibility.
     *
     * @return the sourcecode.
     */
    default String sourceOrGenerateIfNull() {
        return getSource() != null ? getSource() : SourceGenerator.generate(this);
    }

    /**
     * Static helper class for generating source_code from a {@link FlowInterface} object.
     *
     * <p>
     * This class must only be used for testing purpose or for handling backward-compatibility.
     */
    class SourceGenerator {
        private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofJson()
            .copy()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

        static String generate(final FlowInterface flow) {
            try {
                String json = NON_DEFAULT_OBJECT_MAPPER.writeValueAsString(flow);

                Object map = SourceGenerator.fixSnakeYaml(JacksonMapper.toMap(json));

                String source = JacksonMapper.ofYaml().writeValueAsString(map);

                // remove the revision from the generated source
                return sourceWithoutRevision(source);
            } catch (JsonProcessingException e) {
                return null;
            }
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
            if (object instanceof Map<?, ?> mapValue) {
                return mapValue
                    .entrySet()
                    .stream()
                    .map(entry -> new AbstractMap.SimpleEntry<>(
                        fixSnakeYaml(entry.getKey()),
                        fixSnakeYaml(entry.getValue())
                    ))
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new
                    ));
            } else if (object instanceof Collection<?> collectionValue) {
                return collectionValue
                    .stream()
                    .map(SourceGenerator::fixSnakeYaml)
                    .toList();
            } else if (object instanceof String item) {
                if (item.contains("\n")) {
                    return item.replaceAll("\\s+\\n", "\\\n");
                }
            }
            return object;
        }
    }
}
