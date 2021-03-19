package io.kestra.core.runners.handlebars.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import io.kestra.core.serializers.JacksonMapper;

public enum JsonHelper implements Helper<Object> {
    /**
     * <p>
     * Usage:
     * </p>
     *
     * <pre>
     *    {{json object}}
     * </pre>
     */
    json {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            try {
                return JacksonMapper.ofJson().writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    },

    /**
     * <p>
     * Usage:
     * </p>
     *
     * <pre>
     *    {{json object}}
     * </pre>
     */
    isJson {
        @Override
        public Boolean apply(final Object value, final Options options) {
            try {
                JacksonMapper.ofJson().readTree((String) value);
                return true;
            } catch (JsonProcessingException e) {
                return false;
            }
        }
    }
}

