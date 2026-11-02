package io.kestra.core.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.configuration.PluginsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenPluginDownloaderTest {

    private static final String SNAPSHOT = "io.kestra.test:plugin-test:1.0.0-SNAPSHOT";
    private static final String MISSING_ARTIFACT = "io.kestra.plugin:plugin-that-does-not-exist:1.0.0";

    @TempDir
    Path remote;

    @TempDir
    Path local;

    @Test
    void shouldResolveTheLatestBuildWhenASnapshotIsRepublished() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            publishSnapshotBuild(1, "first build");
            assertThat(Files.readString(Path.of(downloader.resolve(SNAPSHOT).uri()))).isEqualTo("first build");

            publishSnapshotBuild(2, "second build");
            assertThat(Files.readString(Path.of(downloader.resolve(SNAPSHOT).uri()))).isEqualTo("second build");
        }
    }

    @Test
    void shouldListReleaseVersionsFromTheLocalCacheWhenListedAgain() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            publishReleaseVersions("1.0.0");
            assertThat(downloader.listAllVersions("io.kestra.test:plugin-test:LATEST")).containsExactly("1.0.0");

            publishReleaseVersions("1.0.0", "1.1.0");
            assertThat(downloader.listAllVersions("io.kestra.test:plugin-test:LATEST")).containsExactly("1.0.0");
        }
    }

    @Test
    void shouldRejectAsInvalidWhenArtifactIsAbsentFromEveryRepository() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            assertThatThrownBy(() -> downloader.resolve(MISSING_ARTIFACT))
                .isInstanceOf(InvalidException.class)
                .hasMessage("Plugin artifact `%s` was not found in any of the configured Maven repositories (remote).".formatted(MISSING_ARTIFACT));
        }
    }

    @Test
    void shouldRejectAsInvalidWhenNoVersionMatchesTheRange() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            assertThatThrownBy(() -> downloader.resolve("io.kestra.plugin:plugin-that-does-not-exist:[1,2)"))
                .isInstanceOf(InvalidException.class);
        }
    }

    @Test
    void shouldNotBlameTheRequestWhenARepositoryCannotBeReached() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            List<MavenPluginRepositoryConfig> unreachable = List.of(new MavenPluginRepositoryConfig("unreachable", "http://127.0.0.1:1/maven", null));

            assertThatThrownBy(() -> downloader.resolve(MISSING_ARTIFACT, unreachable))
                .isInstanceOf(KestraRuntimeException.class)
                .isNotInstanceOf(InvalidException.class);
        }
    }

    private MavenPluginDownloader downloader() {
        return new MavenPluginDownloader(
            List.of(new MavenPluginRepositoryConfig("remote", remote.toUri().toString(), null)),
            new PluginsConfiguration(local.toString())
        );
    }

    private void publishReleaseVersions(String... versions) throws IOException {
        Path artifactDirectory = Files.createDirectories(remote.resolve("io/kestra/test/plugin-test"));
        StringBuilder versionElements = new StringBuilder();
        for (String version : versions) {
            versionElements.append("<version>").append(version).append("</version>");
        }
        Files.writeString(artifactDirectory.resolve("maven-metadata.xml"), """
            <metadata>
              <groupId>io.kestra.test</groupId>
              <artifactId>plugin-test</artifactId>
              <versioning>
                <versions>%s</versions>
              </versioning>
            </metadata>
            """.formatted(versionElements));
    }

    // Lays the build out as a remote repository with unique snapshot versions does, Sonatype Central included.
    private void publishSnapshotBuild(int buildNumber, String content) throws IOException {
        Path versionDirectory = Files.createDirectories(remote.resolve("io/kestra/test/plugin-test/1.0.0-SNAPSHOT"));
        String timestamp = "20261102.10000" + buildNumber;
        String snapshotVersion = "1.0.0-" + timestamp + "-" + buildNumber;

        Files.writeString(versionDirectory.resolve("plugin-test-" + snapshotVersion + ".jar"), content);
        Files.writeString(versionDirectory.resolve("maven-metadata.xml"), """
            <metadata>
              <groupId>io.kestra.test</groupId>
              <artifactId>plugin-test</artifactId>
              <version>1.0.0-SNAPSHOT</version>
              <versioning>
                <snapshot>
                  <timestamp>%1$s</timestamp>
                  <buildNumber>%2$d</buildNumber>
                </snapshot>
                <lastUpdated>%3$s</lastUpdated>
                <snapshotVersions>
                  <snapshotVersion>
                    <extension>jar</extension>
                    <value>%4$s</value>
                    <updated>%3$s</updated>
                  </snapshotVersion>
                </snapshotVersions>
              </versioning>
            </metadata>
            """.formatted(timestamp, buildNumber, timestamp.replace(".", ""), snapshotVersion));
    }
}
