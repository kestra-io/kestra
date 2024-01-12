package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import lombok.SneakyThrows;
import io.kestra.core.runners.VariableRenderer;

import java.util.Map;

public class EvalHelper implements Helper<String> {
    private final VariableRenderer variableRenderer;

    public EvalHelper(VariableRenderer variableRenderer) {
        this.variableRenderer = variableRenderer;
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public CharSequence apply(final String value, final Options options) {
        String finalTemplate = variableRenderer.renderRecursively(value, (Map<String, Object>) options.context.model());

        return variableRenderer.renderRecursively("{{" + finalTemplate + "}}", (Map<String, Object>) options.context.model());
    }
}

