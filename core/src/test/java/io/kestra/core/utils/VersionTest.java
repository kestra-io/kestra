package io.kestra.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatObject;
import static org.hamcrest.MatcherAssert.assertThat;

class VersionTest {
    
    @Test
    void shouldCreateVersionFromIntegerGivenMajorVersion() {
        Version version = Version.of(1);
        Assertions.assertEquals(1, version.majorVersion());
    }
    
    @Test
    void shouldCreateVersionFromStringGivenMajorVersion() {
        Version version = Version.of("1");
        Assertions.assertEquals(1, version.majorVersion());
    }

    @Test
    void shouldCreateVersionFromStringGivenMajorMinorVersion() {
        Version version = Version.of("1.2");
        Assertions.assertEquals(1, version.majorVersion());
        Assertions.assertEquals(2, version.minorVersion());
    }

    @Test
    void shouldCreateVersionFromStringGivenMajorMinorPatchVersion() {
        Version version = Version.of("1.2.3");
        Assertions.assertEquals(1, version.majorVersion());
        Assertions.assertEquals(2, version.minorVersion());
        Assertions.assertEquals(3, version.patchVersion());
    }

    @Test
    void shouldCreateVersionFromPrefixedStringGivenMajorMinorPatchVersion() {
        Version version = Version.of("v1.2.3");
        Assertions.assertEquals(1, version.majorVersion());
        Assertions.assertEquals(2, version.minorVersion());
        Assertions.assertEquals(3, version.patchVersion());
    }

    @Test
    void shouldCreateVersionFromStringGivenMajorMinorPatchAndQualifierVersion() {
        Version version = Version.of("1.2.3-SNAPSHOT");
        Assertions.assertEquals(1, version.majorVersion());
        Assertions.assertEquals(2, version.minorVersion());
        Assertions.assertEquals(3, version.patchVersion());
        Assertions.assertEquals("SNAPSHOT", version.qualifier().toString());
    }

    @Test
    void shouldCreateVersionFromStringGivenSnapshotSuffixedQualifierVersion() {
        Version version = Version.of("1.2.3-RC0-SNAPSHOT");
        Assertions.assertEquals(1, version.majorVersion());
        Assertions.assertEquals(2, version.minorVersion());
        Assertions.assertEquals(3, version.patchVersion());
        Assertions.assertEquals("RC0-SNAPSHOT", version.qualifier().toString());
    }

    @Test
    void shouldThrowIllegalArgumentGivenInvalidVersion() {
        IllegalArgumentException e = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> Version.of("bad input"));

        Assertions.assertEquals("Invalid version, cannot parse 'bad input'", e.getMessage());
    }

    @Test
    void shouldGetLatestVersionGivenMajorVersions() {
        Version result = Version.getLatest(Version.of("1"), Version.of("3"), Version.of("2"));
        Assertions.assertEquals(Version.of("3"), result);
    }

    @Test
    void shouldGetLatestVersionGivenMajorMinorVersions() {
        Version result = Version.getLatest(Version.of("1.2"), Version.of("1.0"), Version.of("1.10"));
        Assertions.assertEquals(Version.of("1.10"), result);
    }

    @Test
    void shouldGetLatestVersionGivenMajorMinorPatchVersions() {
        Version result = Version.getLatest(Version.of("1.0.9"), Version.of("1.0.10"), Version.of("1.0.11"));
        Assertions.assertEquals(Version.of("1.0.11"), result);
    }

    @Test
    public void shouldGetOldestVersionGivenMajorMinorPatchVersions() {
        Version result = Version.getOldest(Version.of("1.0.9"), Version.of("1.0.10"), Version.of("1.0.11"));
        Assertions.assertEquals(Version.of("1.0.9"), result);
    }

    @Test
    public void shouldGetLatestVersionGivenMajorMinorIncrementalAndSimpleQualifierVersions() {
        Version result = Version.getLatest(Version.of("1.0.0"), Version.of("1.0.0-SNAPSHOT"));
        Assertions.assertEquals(Version.of("1.0.0"), result);

        result = Version.getLatest(Version.of("1.0.0-ALPHA"), Version.of("1.0.0-BETA"));
        Assertions.assertEquals(Version.of("1.0.0-BETA"), result);

        result = Version.getLatest(Version.of("1.0.0-RELEASE"), Version.of("1.0.0-SNAPSHOT"));
        Assertions.assertEquals(Version.of("1.0.0-RELEASE"), result);

        result = Version.getLatest(Version.of("1.0.0-RC10"), Version.of("1.0.0-RC12"));
        Assertions.assertEquals(Version.of("1.0.0-RC12"), result);

        result = Version.getLatest(Version.of("1.0.0-rc.10"), Version.of("1.0.0-rc.12"));
        Assertions.assertEquals(Version.of("1.0.0-rc.12"), result);
    }

    @Test
    void shouldReturnTrueForEqualsGivenDifferentCase() {
        Assertions.assertEquals(Version.of("1.0.0-rc.1"), Version.of("1.0.0-RC.1"));
    }

    @Test
    void shouldNotFailGivenUnknownQualifier() {
        Assertions.assertDoesNotThrow(() -> Version.of("1.0.0-custom10"));
        Version result = Version.getLatest(Version.of("1.0.0-custom10"), Version.of("1.0.0-SNAPSHOT"));
        Assertions.assertEquals(Version.of("1.0.0-SNAPSHOT"), result);
    }

    @Test
    void shouldReturnTrueGivenBeforeVersion() {
        Assertions.assertTrue(Version.of("1.0.0").isBefore(Version.of("1.0.1")));
        Assertions.assertTrue(Version.of("1.0.0").isBefore(Version.of("1.1.0")));
        Assertions.assertTrue(Version.of("1.0.0").isBefore(Version.of("2.0.0")));
        Assertions.assertTrue(Version.of("1.0.0-SNAPSHOT").isBefore(Version.of("1.0.0")));
    }

    @Test
    void shouldReturnFalseGivenNonBeforeVersion() {
        Assertions.assertFalse(Version.of("1.0.0").isBefore(Version.of("1.0.0")));
        Assertions.assertFalse(Version.of("1.0.1").isBefore(Version.of("1.0.0")));
        Assertions.assertFalse(Version.of("1.1.0").isBefore(Version.of("1.0.0")));
        Assertions.assertFalse(Version.of("2.0.0").isBefore(Version.of("2.0.0")));
        Assertions.assertFalse(Version.of("1.0.0").isBefore(Version.of("1.0.0-SNAPSHOT")));
    }

    @Test
    public void shouldGetStableVersionGivenMajorMinorPatchVersion() {
        // Given
        List<Version> versions = List.of(Version.of("1.2.1"), Version.of("1.2.3"), Version.of("0.99.0"));
        
        // When - Then
        assertThatObject(Version.getStable(Version.of("1.2.1"), versions)).isEqualTo(Version.of("1.2.1"));
        assertThatObject(Version.getStable(Version.of("1.2.0"), versions)).isNull();
        assertThatObject(Version.getStable(Version.of("1.2.4"), versions)).isNull();
    }
    
    @Test
    public void shouldGetStableGivenMajorAndMinorVersionOnly() {
        // Given
        List<Version> versions = List.of(Version.of("1.2.1"), Version.of("1.2.3"), Version.of("0.99.0"));
        
        // When - Then
        assertThatObject(Version.getStable(Version.of("1.2"), versions)).isEqualTo(Version.of("1.2.3"));
    }
    
    @Test
    public void shouldGetStableGivenMajorVersionOnly() {
        // Given
        List<Version> versions = List.of(Version.of("1.2.1"), Version.of("1.2.3"), Version.of("0.99.0"));
        
        // When - Then
        assertThatObject(Version.getStable(Version.of("1"), versions)).isEqualTo(Version.of("1.2.3"));
    }

    @Test
    public void shouldGetNullForStableGivenMajorAndMinorVersionOnly() {
        // Given
        List<Version> versions = List.of(Version.of("1.2.1"), Version.of("1.2.3"), Version.of("0.99.0"));
        
        // When - Then
        assertThatObject(Version.getStable(Version.of("2.0"), versions)).isNull();
        assertThatObject(Version.getStable(Version.of("0.1"), versions)).isNull();
    }
    
    @Test
    public void shouldGetNullForStableGivenMajorVersionOnly() {
        // Given
        List<Version> versions = List.of(Version.of("1.2.1"), Version.of("1.2.3"), Version.of("0.99.0"));
        
        // When - Then
        assertThatObject(Version.getStable(Version.of("2"), versions)).isNull();
    }
}