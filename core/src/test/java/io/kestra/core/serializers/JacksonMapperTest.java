package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;
import org.junitpioneer.jupiter.RetryingTest;

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
    @DefaultTimeZone("Europe/Athens")
    void json() throws IOException {
        ObjectMapper mapper = JacksonMapper
            .ofJson()
            .copy()
            .setTimeZone(TimeZone.getDefault());

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        Pojo deserialize = mapper.readValue(s, Pojo.class);

        test(original, deserialize);
    }

    @Test
    @DefaultTimeZone("Europe/Athens")
    void ion() throws IOException {
        ObjectMapper mapper = JacksonMapper.ofIon();

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        assertThat(s, containsString("nullable:null"));
        Pojo deserialize = mapper.readValue(s, Pojo.class);
        test(original, deserialize);
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
