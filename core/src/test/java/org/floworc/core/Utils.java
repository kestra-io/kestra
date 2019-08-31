package org.floworc.core;

import org.floworc.core.models.flows.Flow;
import org.floworc.core.serializers.YamlFlowParser;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

abstract public class Utils {
    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

    public static Flow parse(String path) throws IOException {
        URL resource = Utils.class.getClassLoader().getResource(path);
        assertNotNull(resource);

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}
