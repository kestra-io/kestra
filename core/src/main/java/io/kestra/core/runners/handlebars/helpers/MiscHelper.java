package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
            Object result = value;

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
    },

    get {
        @Override
        public Object apply(final Object value, final Options options) {
            String key = options.param(0, options.hash("key"));

            if (key == null) {
                throw new IllegalStateException("Missing 'key' params");
            }


            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;

                if (!map.containsKey(key)) {
                    throw new IllegalStateException("Unable to find key '" + key + "' on '" + value + "'");
                }
                return map.get(key);
            }

            if (value instanceof List) {
                List<?> list = (List<?>) value;
                int arrayIndex = Integer.parseInt(key);

                if (list.size() - 1 > arrayIndex) {
                    throw new IllegalStateException("Unable to find key '" + key + "' on '" + value + "'");
                }
                return list.get(arrayIndex);
            }

            throw new IllegalStateException("Incompatible type '" + value.getClass() + "' for indexOf with  '" + key + "' on '" + value + "'");
        }
    }
}

