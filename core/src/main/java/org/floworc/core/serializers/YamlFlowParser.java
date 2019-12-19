package org.floworc.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.floworc.core.models.flows.Flow;

import java.io.File;
import java.io.IOException;

public class YamlFlowParser {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    public Flow parse(File file) throws IOException {
        Flow flow = mapper.readValue(file, Flow.class);
        Validator.isValid(flow);
        return flow;
    }
}

