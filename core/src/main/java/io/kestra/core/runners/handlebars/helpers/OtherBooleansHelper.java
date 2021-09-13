package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

public enum OtherBooleansHelper implements Helper<Object> {
    /**
     * <p>
     * Usage: return true if null
     * </p>
     *
     * <pre>
     *    {{isNotNull c}}
     * </pre>
     */
    isNull {
        @Override
        public Object apply(final Object value, final Options options) {
            return value == null;
        }
    },

    /**
     * <p>
     * Usage: return true if not null
     * </p>
     *
     * <pre>
     *    {{isNotNull c}}
     * </pre>
     */
    isNotNull {
        @Override
        public Object apply(final Object value, final Options options) {
            return value != null;
        }
    },
}

