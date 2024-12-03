package io.kestra.core.serializers;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.DecimalUtils;

import java.io.IOException;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;

public class DurationDeserializer extends com.fasterxml.jackson.datatype.jsr310.deser.DurationDeserializer {
    @Serial
    private static final long serialVersionUID = 1L;

    // durations can be a string with a number which is not taken into account as it should not happen
    // we specialize the Duration deserialization from string to support that
    @Override
    protected Duration _fromString(JsonParser parser, DeserializationContext ctxt, String value0) throws IOException {
        String value = value0.trim();
        if (value.isEmpty()) {
            // 22-Oct-2020, tatu: not sure if we should pass original (to distinguish
            //   b/w empty and blank); for now don't which will allow blanks to be
            //   handled like "regular" empty (same as pre-2.12)
            return _fromEmptyString(parser, ctxt, value);
        }
        // 30-Sep-2020: Should allow use of "Timestamp as String" for
        //     some textual formats
        if (ctxt.isEnabled(StreamReadCapability.UNTYPED_SCALARS)
            && _isValidTimestampString(value)) {
            return _fromTimestamp(ctxt, NumberInput.parseLong(value));
        }

        // These are the only lines we changed from the default impl: we check for a float as string and parse it
        if (_isFloat(value)) {
            double d = Double.parseDouble(value);
            BigDecimal bigDecimal = BigDecimal.valueOf(d);
            return DecimalUtils.extractSecondsAndNanos(bigDecimal, Duration::ofSeconds);
        }

        try {
            return Duration.parse(value);
        } catch (DateTimeException e) {
            return _handleDateTimeException(ctxt, e, value);
        }
    }

    // this method is inspired by _isIntNumber but allow the decimal separator '.'
    private boolean _isFloat(String text) {
        final int len = text.length();
        if (len > 0) {
            char c = text.charAt(0);
            // skip leading sign (plus not allowed for strict JSON numbers but...)
            int i;

            if (c == '-' || c == '+') {
                if (len == 1) {
                    return false;
                }
                i = 1;
            } else {
                i = 0;
            }
            // We will allow leading
            for (; i < len; ++i) {
                int ch = text.charAt(i);
                if (ch == '.') {
                    continue;
                }
                if (ch > '9' || ch < '0') {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
