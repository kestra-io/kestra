package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@NoArgsConstructor
@Slf4j
public class FlowWithSource {
    private Flow flow;
    private String sourceCode;

    public FlowWithSource(Flow flow, String sourceCode) {
        this.flow = flow;
        this.sourceCode = sourceCode;

        // previously, we insert source on database keeping default value (like deleted, ...)
        // if the previous serialization is the same as actual one, we use a clean version removing them
        try {
            if (JacksonMapper.ofYaml().writeValueAsString(flow).equals(sourceCode)) {
                this.sourceCode = toYamlWithoutDefault(flow);
            }
        } catch (JsonProcessingException e) {
            log.warn("Unable to convert flow json '{}' '{}'({})", flow.getNamespace(), flow.getId(), flow.getRevision(), e);
        }

        // same here but with version that don't make any sense on the source code, so removing it
        this.sourceCode = FlowService.cleanupSource(this.sourceCode);
    }

    private static String toYamlWithoutDefault(Object Object) throws JsonProcessingException {
        return JacksonMapper.ofYaml()
            .writeValueAsString(
                JacksonMapper
                    .ofJson()
                    .readTree(
                        JacksonMapper
                            .ofJson()
                            .setSerializationInclusion(
                                JsonInclude.Include.NON_DEFAULT)
                            .writeValueAsString(Object)
                    )
            );
    }
}
