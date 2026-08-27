package io.kestra.cli.schema;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginsSchemaCommandTest {

    @Test
    void shouldReturnThePackageItselfForASinglePackage() {
        assertThat(PluginsSchemaCommand.commonPackagePrefix(List.of("io.kestra.plugin.transform.jsonata")))
            .contains("io.kestra.plugin.transform.jsonata");
    }

    @Test
    void shouldReturnTheCommonPrefixAcrossPackages() {
        assertThat(
            PluginsSchemaCommand.commonPackagePrefix(
                List.of(
                    "io.kestra.plugin.aws.s3",
                    "io.kestra.plugin.aws.sqs",
                    "io.kestra.plugin.aws"
                )
            )
        ).contains("io.kestra.plugin.aws");
    }

    @Test
    void shouldReturnEmptyWhenPackagesShareNoPrefix() {
        assertThat(PluginsSchemaCommand.commonPackagePrefix(List.of("io.kestra.plugin.aws", "com.acme.plugin")))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNoPackages() {
        assertThat(PluginsSchemaCommand.commonPackagePrefix(List.of())).isEmpty();
    }
}
