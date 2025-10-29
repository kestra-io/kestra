package io.kestra.core.plugins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * A specific plugin artifact.
 *
 * @param groupId    the group identifier of this plugin artifact
 * @param artifactId the artifact identifier of this plugin artifact
 * @param version    the version of this plugin artifact
 * @param uri        the location of this plugin artifact.
 */
@Builder(toBuilder = true)
public record PluginArtifact(
    String groupId,
    String artifactId,
    String extension,
    String classifier,
    String version,
    URI uri
) implements Comparable<PluginArtifact> {
    
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile(
        "([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)"
    );
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
        "^(?<groupId>[\\w_]+)__(?<artifactId>[\\w-_]+)(?:__(?<classifier>[\\w-_]+))?__(?<version>\\d+_\\d+_\\d+(-[a-zA-Z0-9-]+)?|([a-zA-Z0-9]+))\\.jar$"
    );

    public static final String JAR_EXTENSION = "jar";
    public static final String KESTRA_GROUP_ID = "io.kestra";
    
    /**
     * Static helper method for constructing a new {@link PluginArtifact} from a JAR file.
     *
     * @param file The JAR file.
     * @return a new {@link PluginArtifact}.
     * @throws IOException if the file cannot be read.
     */
    public static PluginArtifact fromFile(final File file) throws IOException {
        try (JarFile jarFile = new JarFile(file)) {
            Manifest manifest = jarFile.getManifest();
            final String version = manifest.getMainAttributes().getValue("X-Kestra-Version");
            final String artifactId = manifest.getMainAttributes().getValue("X-Kestra-Name");
            final String groupId = manifest.getMainAttributes().getValue("X-Kestra-Group");

            return new PluginArtifact(
                groupId,
                artifactId,
                "jar",
                null,
                version,
                file.toURI()
            );
        }
    }

    /**
     * Static helper method for constructing a new {@link PluginArtifact} from an artifact string coordinates.
     *
     * @param coordinates The artifact's coordinates
     * @return a new {@link PluginArtifact}.
     */
    public static PluginArtifact fromCoordinates(final String coordinates) {
        Matcher m = ARTIFACT_PATTERN.matcher(coordinates);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad artifact coordinates " + coordinates
                + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
        }
        return new PluginArtifact(
            m.group(1),
            m.group(2),
            Optional.ofNullable(m.group(4)).filter(not(String::isEmpty)).orElse(JAR_EXTENSION),
            Optional.ofNullable(m.group(6)).filter(not(String::isEmpty)).orElse(null),
            "LATEST".equalsIgnoreCase(m.group(7)) ? "LATEST": m.group(7),
            null
        );
    }

    /**
     * Static helper method for constructing a new {@link PluginArtifact} from an artifact a file name.
     *
     * @param fileName The artifact's file name
     * @return a new {@link PluginArtifact}.
     */
    public static PluginArtifact fromFileName(final String fileName) {
        Matcher matcher = FILENAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid artifact filename '" + fileName + "', expected format is <groupId>__<artifactId>[__<classifier>]__<version>.jar");
        }

        String[] parts = fileName.substring(0, fileName.lastIndexOf(".")).split("__");

        String groupId = parts[0].replace("_", ".");
        String artifactId = parts[1];
        String version;
        String classifier = null; // optional
        if (parts.length == 4) {
            classifier = parts[2];
            version = parts[3].replace("_", ".");
        } else {
            version = parts[2].replace("_", ".");
        }
        return new PluginArtifact(groupId, artifactId, "jar", classifier, version, null);
    }

    public PluginArtifact relocateTo(URI uri) {
        return new PluginArtifact(
            groupId,
            artifactId,
            extension,
            classifier,
            version,
            uri
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toCoordinates();
    }
    
    @JsonIgnore
    public boolean isOfficial() {
        return groupId.startsWith(KESTRA_GROUP_ID);
    }

    public String toCoordinates() {
        return Stream.of(groupId, artifactId, extension, classifier, version)
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .collect(Collectors.joining(":"));
    }

    public String toFileName() {
        String name = Stream.of(
                groupId.replace(".", "_"),
                artifactId,
                classifier,
                version.replace(".", "_")
            )
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .collect(Collectors.joining("__"));
        return name + "." + extension;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(PluginArtifact that) {
        return this.toCoordinates().compareTo(that.toCoordinates());
    }
}
