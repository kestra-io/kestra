package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import lombok.SneakyThrows;
import io.kestra.core.runners.VariableRenderer;

public class EvalHelper implements Helper<String> {
    private final VariableRenderer variableRenderer;

    public EvalHelper(VariableRenderer variableRenderer) {
        this.variableRenderer = variableRenderer;
    }

    @SneakyThrows
    @Override
    public CharSequence apply(final String value, final Options options) {
        String finalTemplate = variableRenderer.recursiveRender(value, options.context);

        return variableRenderer.recursiveRender("{{" + finalTemplate + "}}", options.context);
    }
}

