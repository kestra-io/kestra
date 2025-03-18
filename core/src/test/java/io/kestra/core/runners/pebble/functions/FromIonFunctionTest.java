package io.kestra.core.runners.pebble.functions;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class FromIonFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Inject
    StorageInterface storageInterface;

    @Test
    void ionDecodeFunction() throws IllegalVariableEvaluationException {
        String render = variableRenderer.render("{{ fromIon('{date:2024-04-21T23:00:00.000Z, title:\"Main_Page\",views:109787}').title }}", Map.of());
        assertThat(render, is("Main_Page"));

        render = variableRenderer.render("{{ fromIon(null) }}", Map.of());
        assertThat(render, emptyString());
    }

    @Test
    void multiLine() throws IllegalVariableEvaluationException, IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".trs");
        OutputStream output = new FileOutputStream(tempFile);
        for (int i = 0; i < 10; i++) {
            FileSerde.write(output, ImmutableMap.of(
                "id", i,
                "name", "john"
            ));
        }

        Map<String, Object> variables = Map.of(
            "flow", Map.of("id", "test", "namespace", "unit"),
            "execution", Map.of("id", "id-exec")
        );

        URI internalStorageURI = URI.create("/unit/test/executions/id-exec/" + IdUtils.create() + ".ion");
        URI internalStorageFile = storageInterface.put(null, "unit", internalStorageURI, new FileInputStream(tempFile));

        String render = variableRenderer.render("{{ fromIon(read('" + internalStorageFile + "'), allRows=true) }}", variables);
        assertThat(render, containsString("\"id\":0"));
        assertThat(render, containsString("\"id\":9"));

        render = variableRenderer.render("{{ fromIon(read('" + internalStorageFile + "')) }}", variables);
        assertThat(render, containsString("\"id\":0"));
        assertThat(render, not((containsString("\"id\":9"))));
    }


    @Test
    void exception() {
        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromIon() }}", Map.of()));

        assertThrows(IllegalVariableEvaluationException.class, () -> variableRenderer.render("{{ fromIon('{not: ion') }}", Map.of()));
    }
}