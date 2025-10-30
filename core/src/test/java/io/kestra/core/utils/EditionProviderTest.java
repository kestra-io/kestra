package io.kestra.core.utils;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KestraTest
public class EditionProviderTest {
    @Inject
    private EditionProvider editionProvider;

    protected EditionProvider.Edition expectedEdition() {
        return EditionProvider.Edition.OSS;
    }

    @Test
    void shouldReturnCurrentEdition() {
        Assertions.assertEquals(expectedEdition(), editionProvider.get());
    }
}
