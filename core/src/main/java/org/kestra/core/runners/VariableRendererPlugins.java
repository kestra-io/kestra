package org.kestra.core.runners;

import com.github.jknack.handlebars.Helper;

public interface VariableRendererPlugins <T> {
    String name();

    Helper<T> helper();
}
