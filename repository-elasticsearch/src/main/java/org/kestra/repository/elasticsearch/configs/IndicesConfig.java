package org.kestra.repository.elasticsearch.configs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import lombok.Getter;
import lombok.SneakyThrows;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.utils.MapUtils;

import java.net.URL;
import java.util.Map;
import java.util.Objects;

@EachProperty("kestra.elasticsearch.indices")
@Getter
public class IndicesConfig {
    private final static ObjectMapper yamlMapper = JacksonMapper.ofYaml();
    private final static ObjectMapper jsonMapper = JacksonMapper.ofJson();
    private final static TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};

    String name;

    Class<?> cls;

    String index;

    String settings;

    String mappingFile;

    public IndicesConfig(@Parameter String name) {
        this.name = name;
    }

    @SneakyThrows
    private Map<String, Object> readYamlFile(String path) {
        URL url = Objects.requireNonNull(this.getClass().getClassLoader()
            .getResource(path));

        return yamlMapper.readValue(url, typeReference);
    }

    @SneakyThrows
    public String getSettingsContent() {
        Map<String, Object> defaults = this.readYamlFile("settings.yml");

        Map<String, Object> override = this.getSettings() == null ? Map.of() : jsonMapper.readValue(this.getSettings(), typeReference);

        return jsonMapper.writeValueAsString(
            MapUtils.merge(defaults, override)
        );
    }

    @SneakyThrows
    public String getMappingContent() {
        return jsonMapper.writeValueAsString(
            this.readYamlFile("mappings/" + this.getMappingFile() + ".yml")
        );
    }
}

