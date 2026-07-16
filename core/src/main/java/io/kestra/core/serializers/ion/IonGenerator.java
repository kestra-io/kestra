package io.kestra.core.serializers.ion;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import com.amazon.ion.IonWriter;
import com.amazon.ion.Timestamp;

import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.io.IOContext;

public class IonGenerator extends tools.jackson.dataformat.ion.IonGenerator {
    public IonGenerator(ObjectWriteContext writeCtxt, IOContext ctxt, int jsonFeatures, int ionFeatures, IonWriter ion, boolean ionWriterIsManaged, Closeable dst) {
        super(writeCtxt, ctxt, jsonFeatures, ionFeatures, ion, ionWriterIsManaged, dst);
    }

    public void writeString(Object value, String serialized) throws JacksonException {
        _verifyValueWrite("write " + value.getClass().getName() + " value");

        try {
            _writer.addTypeAnnotation(value.getClass().getSimpleName());
            _writer.writeString(serialized);
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
    }

    public void writeDate(Instant value) throws JacksonException {
        _verifyValueWrite("write LocalDateTime value");

        try {
            _writer.writeTimestamp(Timestamp.forDateZ(Date.from(value)));
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
    }

    public void writeDate(LocalDate value) throws JacksonException {
        _verifyValueWrite("write LocalDate value");

        try {
            _writer.writeTimestamp(Timestamp.forDay(value.getYear(), value.getMonth().getValue(), value.getDayOfMonth()));
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
    }
}
