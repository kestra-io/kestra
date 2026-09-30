package io.kestra.tests.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Excludes {@link io.kestra.cli.Kestra}, whose simple name coincidentally matches
 * {@code KestraTest}'s suffix-stripped pairing in {@code test_same_package}, which has nothing to do
 * with testing it.
 */
public final class ExcludeKestraCliEntryPoint implements ImportOption {
    @Override
    public boolean includes(Location location) {
        return !location.contains("io/kestra/cli/Kestra.class");
    }
}
