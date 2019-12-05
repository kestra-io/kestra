package org.floworc.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.value.ValueException;
import org.floworc.core.exceptions.InvalidFlowException;
import org.floworc.core.models.flows.Flow;

import javax.validation.ConstraintViolation;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public class YamlFlowParser {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    public Flow parse(File file) throws IOException {
        Flow flow = mapper.readValue(file, Flow.class);
        Validator.isValid(flow);
        return flow;
    }
}

