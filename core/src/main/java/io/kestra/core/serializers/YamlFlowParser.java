package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.helpers.HandleBarDeserializer;
import jakarta.inject.Singleton;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import javax.validation.ConstraintViolationException;

@Singleton
public class YamlFlowParser {
    public static final String CONTEXT_FLOW_DIRECTORY = "flowDirectory";

    private static final ObjectMapper mapper = JacksonMapper.ofYaml()
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .registerModule(new SimpleModule("HandleBarDeserializer")
            .addDeserializer(String.class, new HandleBarDeserializer())
        );

    public static boolean isValidExtension(Path path) {
        return FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yaml") || FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yml");
    }

    public Flow parse(String input) {
        return readFlow(mapper, input);
    }

    public Flow parse(File file) throws ConstraintViolationException {

        try {
            String input = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
            return readFlow(
                mapper.copy()
                .setInjectableValues(new InjectableValues.Std()
                    .addValue(CONTEXT_FLOW_DIRECTORY, file.getAbsoluteFile().getParentFile().getAbsolutePath())
                ),
                input
            );

        } catch (IOException e) {
            throw new ConstraintViolationException(
                "Illegal flow path:" + e.getMessage(),
                Collections.singleton(
                    ManualConstraintViolation.of(
                        e.getMessage(),
                        file,
                        File.class,
                        "flow",
                        file.getAbsolutePath()
                    )
                )
            );
        }
    }

    private Flow readFlow(ObjectMapper mapper, String input) {
        try {
            return mapper.readValue(input, Flow.class);
        } catch (JsonProcessingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw (ConstraintViolationException) e.getCause();
            } else {
                throw new ConstraintViolationException(
                    "Illegal flow yaml:" + e.getMessage(),
                    Collections.singleton(
                        ManualConstraintViolation.of(
                            "Caused by: " + e.getCause() + "\nMessage: " + e.getMessage(),
                            input,
                            String.class,
                            "flow",
                            null
                        )
                    )
                );
            }
        }
    }
}

