package org.kestra.task.serdes.avro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Builder
public class AvroConverter {
    @Builder.Default
    private List<String> trueValues = Arrays.asList("t", "true", "enabled", "1", "on", "yes");

    @Builder.Default
    private List<String> falseValues = Arrays.asList("f", "false", "disabled", "0", "off", "no", "");

    @Builder.Default
    private List<String> nullValues = Arrays.asList(
        "",
        "#N/A",
        "#N/A N/A",
        "#NA",
        "-1.#IND",
        "-1.#QNAN",
        "-NaN",
        "1.#IND",
        "1.#QNAN",
        "NA",
        "n/a",
        "nan",
        "null"
    );

    @Builder.Default
    private String dateFormat = "yyyy-MM-dd[XXX]";

    @Builder.Default
    private String timeFormat = "HH:mm[:ss][.SSSSSS][XXX]";

    @Builder.Default
    private String datetimeFormat = "yyyy-MM-dd'T'HH:mm[:ss][.SSSSSS][XXX]";

    public static GenericData genericData() {
        GenericData genericData = new GenericData();
        genericData.addLogicalTypeConversion(new Conversions.UUIDConversion());
        genericData.addLogicalTypeConversion(new Conversions.DecimalConversion());
        genericData.addLogicalTypeConversion(new TimeConversions.DateConversion());
        genericData.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());
        genericData.addLogicalTypeConversion(new TimeConversions.TimeMillisConversion());
        genericData.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
        genericData.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());

        return genericData;
    }

    public GenericData.Record fromMap(Schema schema, Map<String, Object> data) throws IllegalRowConvertion {
        GenericData.Record record = new GenericData.Record(schema);

        for (Schema.Field field : schema.getFields()) {
            try {
                record.put(field.name(), convert(field.schema(), data.get(field.name())));
            } catch (IllegalCellConversion e) {
                throw new IllegalRowConvertion(data, e);
            }
        }

        return record;
    }

    public GenericData.Record fromArray(Schema schema, List<String> data) throws IllegalRowConvertion {
        HashMap<String, Object> map = new HashMap<>();
        int index = 0;
        for (Schema.Field field : schema.getFields()) {
            map.put(field.name(), data.get(index));
            index++;
        }

        return this.fromMap(schema, map);
    }

    @SuppressWarnings("unchecked")
    private Object convert(Schema schema, Object data) throws IllegalCellConversion {
        try {
            if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("decimal")) { // logical
                return this.logicalDecimal(schema, data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("uuid")) {
                return this.logicalUuid(data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("date")) {
                return this.logicalDate(data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("time-millis")) {
                return this.logicalTimeMillis(data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("time-micros")) {
                return this.logicalTimeMicros(data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("timestamp-millis")) {
                return this.logicalTimestampMillis(data);
            } else if (schema.getLogicalType() != null && schema.getLogicalType().getName().equals("timestamp-micros")) {
                return this.logicalTimestampMicros(data);
            } else if (schema.getType() == Schema.Type.RECORD) { // complex
                return fromMap(schema, (Map<String, Object>) data);
            } else if (schema.getType() == Schema.Type.ARRAY) {
                return this.complexArray(schema, data);
            } else if (schema.getType() == Schema.Type.MAP) {
                return this.complexMap(schema, data);
            } else if (schema.getType() == Schema.Type.UNION) {
                return this.complexUnion(schema, data);
            } else if (schema.getType() == Schema.Type.FIXED) {
                return this.complexFixed(schema, data);
            } else if (schema.getType() == Schema.Type.ENUM) {
                return this.complexEnum(schema, data);
            } else if (schema.getType() == Schema.Type.NULL) { // primitive
                return this.primitiveNull(data);
            } else if (schema.getType() == Schema.Type.INT) {
                return this.primitiveInt(data);
            } else if (schema.getType() == Schema.Type.FLOAT) {
                return this.primitiveFloat(data);
            } else if (schema.getType() == Schema.Type.DOUBLE) {
                return this.primitiveDouble(data);
            } else if (schema.getType() == Schema.Type.LONG) {
                return this.primitiveLong(data);
            } else if (schema.getType() == Schema.Type.BOOLEAN) {
                return this.primitiveBool(data);
            } else if (schema.getType() == Schema.Type.STRING) {
                return this.primitiveString(data);
            } else if (schema.getType() == Schema.Type.BYTES) {
                return this.primitiveBytes(data);
            } else {
                throw new IllegalArgumentException("Invalid schema \"" + schema.getType() + "\"");
            }
        } catch (Throwable e) {
            throw new IllegalCellConversion(schema, data, e);
        }
    }

    @SuppressWarnings("UnpredictableBigDecimalConstructorCall")
    private BigDecimal logicalDecimal(Schema schema, Object data) {
        int scale = ((LogicalTypes.Decimal) schema.getLogicalType()).getScale();
        int precision = ((LogicalTypes.Decimal) schema.getLogicalType()).getPrecision();
        double multiply = Math.pow(10D, precision - scale * 1D);

        BigDecimal value;

        if (data instanceof String) {
            value = new BigDecimal(((String) data));
        } else if (data instanceof Long) {
            value = BigDecimal.valueOf((long) ((long) data * multiply), scale);
        } else if (data instanceof Integer) {
            value = BigDecimal.valueOf((int) ((int) data * multiply), scale);
        } else if (data instanceof Double) {
            value = new BigDecimal((double) data, new MathContext(precision));
        } else if (data instanceof Float) {
            value = new BigDecimal((float) data, new MathContext(precision));
        } else {
            value = (BigDecimal) data;
        }

        value = value.setScale(scale, RoundingMode.HALF_EVEN);

        return value;
    }

    private UUID logicalUuid(Object data) {
        if (data instanceof String) {
            return UUID.fromString((String) data);
        } else {
            return (UUID) data;
        }
    }

    private LocalDate logicalDate(Object data) {
        if (data instanceof String) {
            return LocalDate.parse((String) data, DateTimeFormatter.ofPattern(this.dateFormat));
        } else {
            return (LocalDate) data;
        }
    }

    private LocalTime logicalTimeMillis(Object data) {
        if (data instanceof String) {
            return LocalTime.parse((String) data, DateTimeFormatter.ofPattern(this.timeFormat));
        } else {
            return (LocalTime) data;
        }
    }

    private LocalTime logicalTimeMicros(Object data) {
        if (data instanceof String) {
            return LocalTime.parse((String) data, DateTimeFormatter.ofPattern(this.timeFormat));
        } else {
            return (LocalTime) data;
        }
    }

    private Instant logicalTimestampMillis(Object data) {
        if (data instanceof String) {
            return LocalDateTime.parse((String) data, DateTimeFormatter.ofPattern(this.datetimeFormat)).toInstant(ZoneOffset.UTC);
        } else {
            return (Instant) data;
        }
    }

    private Instant logicalTimestampMicros(Object data) {
        if (data instanceof String) {
            return LocalDateTime.parse((String) data, DateTimeFormatter.ofPattern(this.datetimeFormat)).toInstant(ZoneOffset.UTC);
        } else {
            return (Instant) data;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> complexArray(Schema schema, Object data) throws IllegalCellConversion {
        Schema elementType = schema.getElementType();

        Collection<Object> list = (Collection<Object>) data;
        List<Object> result = new ArrayList<>();

        for (Object current : list) {
            result.add(this.convert(elementType, current));
        }

        return result;
    }

    private Object complexUnion(Schema schema, Object data) {
        for (Schema current : schema.getTypes()) {
            try {
                return this.convert(current, data);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException("Invalid data for schema \"" + schema.getType() + "\"");
    }

    private GenericData.Fixed complexFixed(Schema schema, Object data) {
        ByteBuffer value = this.primitiveBytes(data);
        int fixedSize = schema.getFixedSize();

        value.position(0);
        int size = value.remaining();

        if (size != fixedSize) {
            throw new IllegalArgumentException("Invalid length for fixed size, found " + size + ", expexted " + fixedSize);
        }

        return new GenericData.Fixed(schema, value.array());
    }

    @SuppressWarnings("unchecked")
    private Map<Utf8, Object> complexMap(Schema schema, Object data) throws IllegalCellConversion {
        Schema valueType = schema.getValueType();

        Map<Object, Object> list = (Map<Object, Object>) data;
        Map<Utf8, Object> result = new HashMap<>();

        for (Map.Entry<Object, Object> current : list.entrySet()) {
            result.put(
                new Utf8(this.primitiveString(current.getKey()).getBytes()),
                this.convert(valueType, current.getValue())
            );
        }

        return result;
    }

    private GenericData.EnumSymbol complexEnum(Schema schema, Object data) {
        String value = this.primitiveString(data);
        List<String> symbols = schema.getEnumSymbols();

        if (!symbols.contains(value)) {
            throw new IllegalArgumentException("Invalid enum value, found " + value + ", expexted " + symbols);
        }

        return new GenericData.EnumSymbol(schema, value);
    }

    private Integer primitiveNull(Object data) {
        if (data instanceof String && this.contains(this.nullValues, (String) data)) {
            return null;
        } else if (data == null) {
            return null;
        } else {
            throw new IllegalArgumentException("Unknown type for null values, found " + data.getClass().getName());
        }
    }

    private Integer primitiveInt(Object data) {
        if (data instanceof String) {
            return Integer.valueOf((String) data);
        } else {
            return (int) data;
        }
    }

    private Long primitiveLong(Object data) {
        if (data instanceof String) {
            return Long.valueOf((String) data);
        } else if (data instanceof Integer) {
            return (long) ((int) data);
        } else {
            return (long) data;
        }
    }

    private Float primitiveFloat(Object data) {
        if (data instanceof String) {
            return Float.valueOf((String) data);
        } else if (data instanceof Integer) {
            return (float) ((int) data);
        } else if (data instanceof Double) {
            return (float) ((double) data);
        } else {
            return (float) data;
        }
    }

    private Double primitiveDouble(Object data) {
        if (data instanceof String) {
            return Double.valueOf((String) data);
        } else if (data instanceof Integer) {
            return (double) ((int) data);
        } else if (data instanceof Float) {
            return (double) ((float) data);
        } else {
            return (double) data;
        }
    }

    public Boolean primitiveBool(Object data) {
        if (data instanceof String && this.contains(this.trueValues, (String) data)) {
            return true;
        } else if (data instanceof String && this.contains(this.falseValues, (String) data)) {
            return false;
        } else if (data instanceof Integer && (int) data == 1) {
            return true;
        } else if (data instanceof Integer && (int) data == 0) {
            return false;
        } else {
            return (boolean) data;
        }
    }

    public String primitiveString(Object data) {
        return String.valueOf(data);
    }

    public ByteBuffer primitiveBytes(Object data) {
        return ByteBuffer.wrap(this.primitiveString(data).getBytes());
    }

    private boolean contains(List<String> list, String data) {
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(data));
    }

    @Getter
    public static class IllegalRowConvertion extends Exception {
        private static ObjectMapper mapper = new ObjectMapper();
        private Object data;

        public IllegalRowConvertion(Map<String, Object> data, Throwable e) {
            super(e);

            this.data = data;
        }

        @Override
        public String toString() {
            try {
                return super.toString() + " on line with data [" + mapper.writeValueAsString(data) + "]";
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }
    }

    @Getter
    public static class IllegalCellConversion extends Exception {
        private static ObjectMapper mapper = new ObjectMapper();
        private Object data;
        private Schema schema;

        public IllegalCellConversion(Schema schema, Object data, Throwable e) {
            super(e);

            this.schema = schema;
            this.data = data;
        }

        @Override
        public String toString() {
            try {
                return super.toString() + " on cols with data [" + mapper.writeValueAsString(data) + "] and schema [" + schema.toString() + "]";
            } catch (JsonProcessingException e) {
                return super.toString();
            }
        }
    }
}
