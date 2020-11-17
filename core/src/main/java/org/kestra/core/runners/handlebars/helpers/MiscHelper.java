package org.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.util.Arrays;

public enum MiscHelper implements Helper<Object> {
    /**
     * <p>
     * Usage: find the first defined value
     * </p>
     *
     * <pre>
     *    {{defined c.d.e a.b x.y.z}}
     * </pre>
     */
    firstDefined {
        @Override
        public Object apply(final Object value, final Options options) {
            Object result = null;

            int i = 0;
            while (result == null && i < options.params.length) {
                Object param = options.params[i++];

                if (!Handlebars.Utils.isEmpty(param)) {
                    result = param;
                }
            }

            if (result == null) {
                throw new IllegalStateException("Unable to find any defined eval on '" + Arrays.toString(options.params) + "'");
            }

            return result;
        }
    }
}

