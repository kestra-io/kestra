package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
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

    public <T> T parse(String input, Class<T> cls) {
        return readFlow(mapper, input, cls, type(cls));
    }

    private static <T> String type(Class<T> cls) {
        return cls.getSimpleName().toLowerCase();
    }

    public <T> T parse(File file, Class<T> cls) throws ConstraintViolationException {
        try {
            String input = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
            return readFlow(
                mapper.copy()
                    .setInjectableValues(new InjectableValues.Std()
                        .addValue(CONTEXT_FLOW_DIRECTORY, file.getAbsoluteFile().getParentFile().getAbsolutePath())
                    ),
                input,
                cls,
                type(cls)
            );

        } catch (IOException e) {
            throw new ConstraintViolationException(
                "Illegal " + type(cls) + " path:" + e.getMessage(),
                Collections.singleton(
                    ManualConstraintViolation.of(
                        e.getMessage(),
                        file,
                        File.class,
                        type(cls),
                        file.getAbsolutePath()
                    )
                )
            );
        }
    }

    private <T> T readFlow(ObjectMapper mapper, String input, Class<T> objectClass, String resource) {
        try {
            return mapper.readValue(input, objectClass);
        } catch (JsonProcessingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw (ConstraintViolationException) e.getCause();
            } else {
                throw new ConstraintViolationException(
                    "Illegal "+ resource +" yaml:" + e.getMessage(),
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

