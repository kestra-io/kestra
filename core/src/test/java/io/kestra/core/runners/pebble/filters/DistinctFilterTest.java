package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DistinctFilterTest {

    @Inject
    VariableRenderer variableRenderer;

    @Test
    void toDistinctFilter() throws IllegalVariableEvaluationException {
        ZonedDateTime date = ZonedDateTime.parse("2013-09-08T16:19:00+02").withZoneSameLocal(ZoneId.systemDefault());

        ImmutableMap<String, Object> vars = ImmutableMap.of(
            "vars", ImmutableMap.of("second", Map.of(
                "string", "string",
                "int", 1,
                "float", 1.123F,
                "list", Arrays.asList(
                    "one", "two", "one", "three", "four", "five", "three",
                    1, 2, 3, 1, 2, 2,
                    1.123F, 2.123F, 1.123F, 10.000F, 10.000F
                ),
                "bool", true,
                "date", date,
                "map", Map.of(
                    "string", "string",
                    "int", 1,
                    "float", 1.123F
                )
            ))
        );

        //Test rendering the list without the distinct filter
        String render = variableRenderer.render("{{ vars.second.list }}", vars);

        //Verify that the list contains duplicates
        assertThat(render).contains("one");
        assertThat(render).contains("two");
        assertThat(render).contains("three");
        assertThat(render).contains("1");
        assertThat(render).contains("1.123");

        //Apply the distinct filter
        String distinctRender = variableRenderer.render("{{ vars.second.list | distinct }}", vars);

        //Verify that duplicates are removed from the list
        assertThat(distinctRender).isEqualTo("[\"one\",\"two\",\"three\",\"four\",\"five\",1,2,3,1.123,2.123,10.0]");
        assertThat(distinctRender).doesNotContain("one,one"); //Ensure duplicates are removed
        assertThat(distinctRender).startsWith("[");
        assertThat(distinctRender).endsWith("]");

        //Edge case: an empty list
        render = variableRenderer.render("{{ [] | distinct }}", Map.of());
        assertThat(render).isEqualTo("[]");
		
		render = variableRenderer.render("{{ null | distinct }}", Map.of());
        assertThat(render).isEqualTo("null");
    }

    @Test
    void distinctFilterWithInvalidInput() {
        //Test case where input is not a list (should throw exception)
        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ \"string\" | distinct }}", Map.of());
        });
    }

    @Test
    void distinctFilterWithNonListObject() {
        //Test case where the input is an object (should throw exception)
        assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ {key : \"value\"} | distinct }}", Map.of());
        });
    }
}
