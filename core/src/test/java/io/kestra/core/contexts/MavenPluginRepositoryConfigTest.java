package io.kestra.core.contexts;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "maven")
class MavenPluginRepositoryConfigTest {

    @Inject
    List<MavenPluginRepositoryConfig> repositories;

    @Test
    void shouldInjectAllMavenPluginRepositories() {
        Assertions.assertEquals(2, repositories.size());
        assertThat(repositories).containsExactlyInAnyOrder(MavenPluginRepositoryConfig.builder()
            .id("central")
            .url("https://repo.maven.apache.org/maven2/")
            .build(), MavenPluginRepositoryConfig.builder()
            .id("secured")
            .url("https://registry.test.org/maven")
            .basicAuth(new MavenPluginRepositoryConfig.BasicAuth(
                "username",
                "password"
            ))
            .build());
    }
}