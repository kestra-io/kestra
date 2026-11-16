package io.kestra.core.plugins;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import io.kestra.core.contexts.MavenPluginRepositoryConfig;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.configuration.PluginsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
                .hasMessage("Plugin artifact `%s` was not found in any of the configured Maven repositories (remote).".formatted(MISSING_ARTIFACT))
                .hasCauseInstanceOf(ArtifactResolutionException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2)", "LATEST"})
    void shouldRejectAsInvalidWhenNoVersionOfTheArtifactExists(String version) throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            assertThatThrownBy(() -> downloader.resolve("io.kestra.plugin:plugin-that-does-not-exist:" + version))
                .isInstanceOf(InvalidException.class);
        }
    }

    @Test
    void shouldRejectAsInvalidWhenNoPublishedVersionMatchesTheRange() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            publishReleaseVersions("1.0.0");

            assertThatThrownBy(() -> downloader.resolve("io.kestra.test:plugin-test:[2,3)"))
                .isInstanceOf(InvalidException.class)
                .hasMessage("No version matching plugin artifact `io.kestra.test:plugin-test:[2,3)` was found in the configured Maven repositories (remote).");
        }
    }

    @Test
    void shouldRejectAsInvalidWhenNoPublishedVersionMatchesTheRangeAndAnotherRepositoryLacksTheArtifact(@TempDir Path empty) throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            publishReleaseVersions("1.0.0");
            List<MavenPluginRepositoryConfig> emptyRepository = List.of(new MavenPluginRepositoryConfig("empty", empty.toUri().toString(), null));

            assertThatThrownBy(() -> downloader.resolve("io.kestra.test:plugin-test:[2,3)", emptyRepository))
                .isInstanceOf(InvalidException.class)
                .hasMessage("No version matching plugin artifact `io.kestra.test:plugin-test:[2,3)` was found in the configured Maven repositories (remote, empty).");
        }
    }

    @Test
    void shouldRejectAsInvalidWhenTheVersionRangeCannotBeParsed() throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            assertThatThrownBy(() -> downloader.resolve("io.kestra.plugin:plugin-that-does-not-exist:[1,"))
                .isInstanceOf(InvalidException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "[1,2)", "LATEST"})
    void shouldNotBlameTheRequestWhenARepositoryCannotBeReached(String version) throws IOException {
        try (MavenPluginDownloader downloader = downloader()) {
            List<MavenPluginRepositoryConfig> unreachable = List.of(new MavenPluginRepositoryConfig("unreachable", "http://127.0.0.1:1/maven", null));

            assertThatThrownBy(() -> downloader.resolve("io.kestra.plugin:plugin-that-does-not-exist:" + version, unreachable))
                .isInstanceOf(KestraRuntimeException.class)
                .isNotInstanceOf(InvalidException.class);
        }
    }

    @Test
    void shouldFailTheDownloadWhenTheRepositoryStopsSendingBytes() throws IOException {
        CountDownLatch releaseStalledDownload = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(new byte[10]);
                body.flush();
                releaseStalledDownload.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        String url = "http://127.0.0.1:%d/".formatted(server.getAddress().getPort());

        try (MavenPluginDownloader downloader = new MavenPluginDownloader(
            List.of(new MavenPluginRepositoryConfig("stalled", url, null)),
            new PluginsConfiguration(local.toString(), Duration.ofSeconds(1))
        )) {
            // The server stops before the downloader closes, so a resolve left blocked by a regression is released first.
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(30), () -> assertThatThrownBy(() -> downloader.resolve("io.kestra.test:plugin-stalled:1.0.0"))
                    .hasRootCauseInstanceOf(SocketTimeoutException.class));
            } finally {
                releaseStalledDownload.countDown();
                server.stop(0);
            }
        }
    }

    private MavenPluginDownloader downloader() {
        return new MavenPluginDownloader(
            List.of(new MavenPluginRepositoryConfig("remote", remote.toUri().toString(), null)),
            new PluginsConfiguration(local.toString(), null)
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
