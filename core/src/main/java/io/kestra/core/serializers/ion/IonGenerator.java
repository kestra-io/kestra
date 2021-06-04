package io.kestra.core.serializers.ion;

import com.amazon.ion.IonWriter;
import com.amazon.ion.Timestamp;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

public class IonGenerator extends com.fasterxml.jackson.dataformat.ion.IonGenerator {
    public IonGenerator(int jsonFeatures, int ionFeatures, ObjectCodec codec, IonWriter ion, boolean ionWriterIsManaged, IOContext ctxt, Closeable dst) {
        super(jsonFeatures, ionFeatures, codec, ion, ionWriterIsManaged, ctxt, dst);
    }

    public void writeString(Object value, String serialized) throws IOException {
        _verifyValueWrite("write " + value.getClass().getName() + " value");

        _writer.addTypeAnnotation(value.getClass().getSimpleName());
        _writer.writeString(serialized);
    }

    public void writeDate(Instant value) throws IOException {
        _verifyValueWrite("write LocalDateTime value");

        _writer.writeTimestamp(Timestamp.forDateZ(Date.from(value)));
    }

    public void writeDate(LocalDate value) throws IOException {
        _verifyValueWrite("write LocalDate value");

        _writer.writeTimestamp(Timestamp.forDay(value.getYear(), value.getMonth().getValue(), value.getDayOfMonth()));
    }
}
