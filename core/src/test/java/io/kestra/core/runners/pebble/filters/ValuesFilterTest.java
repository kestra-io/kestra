package io.kestra.core.runners.pebble.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ValuesFilterTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void map() throws IllegalVariableEvaluationException, JsonProcessingException {
        ZonedDateTime date = ZonedDateTime.parse("2013-09-08T16:19:00+02").withZoneSameLocal(ZoneId.systemDefault());

        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Map.of(
                "string", "string",
                "bool", true,
                "date", date,
                "map", Map.of(
                    "string", "string",
                    "int", 1,
                    "float", 1.123F
                )
            ))
        );

        String render = variableRenderer.render("{{ vars.second.map | values }}", vars);
        List<Object> list = JacksonMapper.ofJson().readValue(render, new TypeReference<>() {});
        assertThat(list, hasItems("string"));
        assertThat(list, hasItems(1.123));

        render = variableRenderer.render("{{ vars.second | values }}", vars);
        list = JacksonMapper.ofJson().readValue(render, new TypeReference<>() {});
        assertThat(list, hasItems("string"));
        assertThat(list, hasItems(true));
    }
}
