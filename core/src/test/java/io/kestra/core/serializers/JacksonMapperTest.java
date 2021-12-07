package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class JacksonMapperTest {
    Pojo pojo() {
        return new Pojo(
            "te\n\nst",
            Instant.parse("2013-09-08T16:19:12Z"),
            ZonedDateTime.parse("2013-09-08T16:19:12+03:00"),
            null
        );
    }

    @Test
    void json() throws IOException {
        TimeZone timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));

        ObjectMapper mapper = JacksonMapper.ofJson();

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        Pojo deserialize = mapper.readValue(s, Pojo.class);

        test(original, deserialize);

        TimeZone.setDefault(timeZone);
    }

    @Test
    void ion() throws IOException {
        TimeZone timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));

        ObjectMapper mapper = JacksonMapper.ofIon();

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        assertThat(s, containsString("nullable:null"));
        Pojo deserialize = mapper.readValue(s, Pojo.class);
        test(original, deserialize);

        TimeZone.setDefault(timeZone);
    }

    void test(Pojo original, Pojo deserialize) {
        assertThat(deserialize.getString(), is(original.getString()));
        assertThat(deserialize.getInstant().toEpochMilli(), is(original.getInstant().toEpochMilli()));
        assertThat(deserialize.getInstant().toString(), is(original.getInstant().toString()));
        assertThat(deserialize.getZonedDateTime().toEpochSecond(), is(original.getZonedDateTime().toEpochSecond()));
        assertThat(deserialize.getZonedDateTime().getOffset(), is(original.getZonedDateTime().getOffset()));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pojo {
        private String string;
        private Instant instant;
        private ZonedDateTime zonedDateTime;
        private String nullable;
    }
}
