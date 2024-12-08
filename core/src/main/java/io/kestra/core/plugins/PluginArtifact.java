package io.kestra.core.plugins;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
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
public record PluginArtifact(
    String groupId,
    String artifactId,
    String extension,
    String classifier,
    String version,
    URI uri
) {

    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");

    /**
     * Static helper method for constructing a new {@link PluginArtifact} from an artifact string coordinates.
     *
     * @param coordinates The artifact's coordinates
     * @return a new {@link PluginArtifact}.
     */
    public static PluginArtifact of(final String coordinates) {
        Matcher m = ARTIFACT_PATTERN.matcher(coordinates);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad artifact coordinates " + coordinates
                + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
        }
        return new PluginArtifact(
            m.group(1),
            m.group(2),
            Optional.ofNullable(m.group(4)).filter(not(String::isEmpty)).orElse("jar"),
            Optional.ofNullable(m.group(6)).filter(not(String::isEmpty)).orElse(""),
            m.group(7),
            null
        );
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
        return getCoordinates();
    }

    @JsonIgnore
    public String getCoordinates() {
        return Stream.of(groupId, artifactId, extension, classifier, version)
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .collect(Collectors.joining(":"));
    }

    @JsonIgnore
    public String getFileName() {
        String name = Stream.of(groupId, artifactId, classifier, version)
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .collect(Collectors.joining("-"));
        return name +  "." + extension;
    }
}
