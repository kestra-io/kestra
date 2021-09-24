package io.kestra.core.runners.handlebars;

import com.github.jknack.handlebars.Helper;

public interface VariableRendererPlugins <T> {
    String name();

    Helper<T> helper();
}
