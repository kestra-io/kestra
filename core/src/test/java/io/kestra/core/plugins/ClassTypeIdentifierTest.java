package io.kestra.core.plugins;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ClassTypeIdentifierTest {
    @Test
    void caseMatters() {
        String identifier = "io.kestra.core.plugins.serdes.PluginDeserializerTest.TestPlugin";
        assertThat(
            DefaultPluginRegistry.ClassTypeIdentifier.create(identifier).type(),
            is(identifier)
        );
    }
}
