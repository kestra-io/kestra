package org.kestra.core.serializers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.validations.ManualConstraintViolation;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.serializers.helpers.HandleBarDeserializer;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
public class YamlFlowParser {
    public static final String CONTEXT_FLOW_DIRECTORY = "flowDirectory";

    private static final ObjectMapper mapper = JacksonMapper.ofYaml()
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .registerModule(new SimpleModule("HandleBarDeserializer")
            .addDeserializer(String.class, new HandleBarDeserializer())
        );

    @Inject
    private ModelValidator modelValidator;

    public Flow parse(File file) throws ConstraintViolationException {
        Flow flow = readFile(file);

        modelValidator
            .isValid(flow)
            .ifPresent(e -> {
                throw new ConstraintViolationException(
                    "Invalid flow '" + flow.getNamespace() + "." + flow.getId() + "', error: " +
                        e.getConstraintViolations()
                            .stream()
                            .map(r -> {
                                return r.getPropertyPath() + ":" + r.getMessage();
                            })
                            .collect(Collectors.joining("\n -")),
                    e.getConstraintViolations()
                );
            });

        return flow;
    }

    private Flow readFile(File file) {
        try {
            return mapper
                .setInjectableValues(new InjectableValues.Std()
                    .addValue(CONTEXT_FLOW_DIRECTORY, file.getAbsoluteFile().getParentFile().getAbsolutePath())
                )
                .readValue(file, Flow.class);
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw (ConstraintViolationException) e.getCause();
            } else {
                throw new ConstraintViolationException(
                    "Illegal flow yaml:" + e.getMessage(),
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
}

