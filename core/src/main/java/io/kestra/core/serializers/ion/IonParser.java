package io.kestra.core.serializers.ion;

import java.io.IOException;
import java.time.*;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.Timestamp;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;

public class IonParser extends com.fasterxml.jackson.dataformat.ion.IonParser {
    @SuppressWarnings("deprecation")
    public IonParser(IonReader r, IOContext ctxt) {
        super(r, ctxt);
    }

    protected JsonToken _tokenFromType(IonType type) {
        String[] typeAnnotations = _reader.getTypeAnnotations();

        if (typeAnnotations.length > 0) {
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        } else {
            return super._tokenFromType(type);
        }
    }

    @Override
    public Object getEmbeddedObject() throws IOException {
        if (this.getTypeId() != null) {
            if (this.getTypeId().equals(Instant.class.getSimpleName())) {
                return Instant.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(OffsetDateTime.class.getSimpleName())) {
                return OffsetDateTime.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(ZonedDateTime.class.getSimpleName())) {
                return ZonedDateTime.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(LocalDateTime.class.getSimpleName())) {
                return LocalDateTime.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(LocalDate.class.getSimpleName())) {
                return LocalDate.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(OffsetTime.class.getSimpleName())) {
                return OffsetTime.parse(_reader.stringValue());
            } else if (this.getTypeId().equals(LocalTime.class.getSimpleName())) {
                return LocalTime.parse(_reader.stringValue());
            }
        }

        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            if (_reader.getType() == IonType.TIMESTAMP) {
                Timestamp timestamp = _reader.timestampValue();
                Instant instant = timestamp.calendarValue().toInstant();

                // Only a date-precision timestamp is a date: forDateZ keeps the time, so a midnight instant is not one.
                if (!timestamp.getPrecision().includes(Timestamp.Precision.MINUTE)) {
                    return LocalDate.ofInstant(instant, ZoneOffset.UTC);
                }

                Integer offsetMinutes = timestamp.getLocalOffset();
                if (offsetMinutes == null || offsetMinutes == 0) {
                    return instant;
                }

                return instant.atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toZonedDateTime();
            }
        }

        return super.getEmbeddedObject();
    }
}
