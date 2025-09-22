package io.kestra.plugin.core.storage;

import com.google.common.io.CharStreams;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.IdUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ConcatTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    void run(Boolean json) throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = ConcatTest.class.getClassLoader().getResource("application-test.yml");

        File file = new File(Objects.requireNonNull(ConcatTest.class.getClassLoader()
            .getResource("application-test.yml"))
            .toURI());

        URI put = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/get-%s.yml".formatted(IdUtils.create())),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        List<String> files = Arrays.asList(put.toString(), put.toString());

        Concat result = Concat.builder()
            .files(json ? JacksonMapper.ofJson().writeValueAsString(files) : files)
            .separator(Property.ofValue("\n"))
            .extension(Property.ofValue(".yml"))
            .build();

        Concat.Output run = result.run(runContext);
        String s = CharStreams.toString(new InputStreamReader(new FileInputStream(file)));


        assertThat(CharStreams.toString(new InputStreamReader(storageInterface.get(MAIN_TENANT, null, run.getUri())))).isEqualTo(s + "\n" + s + "\n");
        assertThat(run.getUri().getPath()).endsWith(".yml");
    }

    @Test
    void list() throws Exception {
        this.run(false);
    }

    @Test
    void json() throws Exception {
        this.run(true);
    }
}
