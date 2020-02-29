package org.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.models.flows.Flow;

import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
public class YamlFlowParser {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    @Inject
    private ModelValidator modelValidator;

    public Flow parse(File file) throws IOException {
        Flow flow = mapper.readValue(file, Flow.class);

        modelValidator
            .isValid(flow)
            .ifPresent(e -> {
                throw new ConstraintViolationException(
                    "Invalid flow '" + flow.getNamespace() + "." + flow.getId() + "', error: " + e.getConstraintViolations().toString(),
                    e.getConstraintViolations()
                );
            });

        return flow;
    }
}

