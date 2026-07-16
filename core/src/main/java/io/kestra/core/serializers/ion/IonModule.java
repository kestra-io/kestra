package io.kestra.core.serializers.ion;

import java.io.Serial;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.Version;
import tools.jackson.core.json.PackageVersion;
import tools.jackson.core.util.VersionUtil;
import tools.jackson.databind.JacksonModule.SetupContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdScalarSerializer;

@SuppressWarnings({ "serial", "this-escape" })
public class IonModule extends SimpleModule {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Version VERSION = VersionUtil.parseVersion(
        "0.0.1",
        "io.kestra",
        "core"
    );

    public IonModule() {
        super(VERSION);
        addSerializer(LocalDate.class, new LocalDateSerializer());
        addSerializer(Instant.class, new InstantSerializer());

        addSerializer(OffsetDateTime.class, new StringTypedSerializer<>(OffsetDateTime.class, o -> o.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        addSerializer(ZonedDateTime.class, new StringTypedSerializer<>(ZonedDateTime.class, o -> o.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)));
        addSerializer(LocalDateTime.class, new StringTypedSerializer<>(LocalDateTime.class, o -> o.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        addSerializer(OffsetTime.class, new StringTypedSerializer<>(OffsetTime.class, o -> o.format(DateTimeFormatter.ISO_OFFSET_TIME)));
        addSerializer(LocalTime.class, new StringTypedSerializer<>(LocalTime.class, o -> o.format(DateTimeFormatter.ISO_LOCAL_TIME)));
    }

    @Override
    public String getModuleName() {
        return getClass().getName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
    }

    public static class StringTypedSerializer<T> extends StdScalarSerializer<T> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Function<T, String> mapper;

        protected StringTypedSerializer(Class<T> cls, Function<T, String> mapper) {
            super(cls);
            this.mapper = mapper;
        }

        @Override
        public void serialize(T value, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
            ((IonGenerator) jsonGenerator).writeString(value, mapper.apply(value));
        }
    }

    public static class InstantSerializer extends StdScalarSerializer<Instant> {
        @Serial
        private static final long serialVersionUID = 1L;

        protected InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant date, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
            ((IonGenerator) jsonGenerator).writeDate(date);
        }
    }

    public static class LocalDateSerializer extends StdScalarSerializer<LocalDate> {
        @Serial
        private static final long serialVersionUID = 1L;

        protected LocalDateSerializer() {
            super(LocalDate.class);
        }

        @Override
        public void serialize(LocalDate date, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
            ((IonGenerator) jsonGenerator).writeDate(date);
        }
    }
}
