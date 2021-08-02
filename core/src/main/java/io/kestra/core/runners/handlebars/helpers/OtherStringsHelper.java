package io.kestra.core.runners.handlebars.helpers;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.Validate.validIndex;

public enum OtherStringsHelper implements Helper<Object> {
    substringBefore {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            validIndex(options.params, 0, "Required valid separator: ");
            String seps = options.param(0);

            return StringUtils.substringBefore(value.toString(), seps);
        }
    },
    substringAfter {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            validIndex(options.params, 0, "Required valid separator: ");
            String seps = options.param(0);

            return StringUtils.substringAfter(value.toString(), seps);
        }
    },
    substringBeforeLast {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            validIndex(options.params, 0, "Required valid separator: ");
            String seps = options.param(0);

            return StringUtils.substringBeforeLast(value.toString(), seps);
        }
    },
    substringAfterLast {
        @Override
        public CharSequence apply(final Object value, final Options options) {
            validIndex(options.params, 0, "Required valid separator: ");
            String seps = options.param(0);

            return StringUtils.substringAfterLast(value.toString(), seps);
        }
    },
}

