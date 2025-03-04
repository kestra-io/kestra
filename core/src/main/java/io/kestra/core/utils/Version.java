package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A version class which supports the following pattern :
 * <p>
 *  <major version>.<minor version>.<incremental version>-<qualifier>
 * <p>
 *  Supported qualifier are : alpha, beta, snapshot, rc, release.
 */
public class Version implements Comparable<Version> {

    public static final Version ZERO = new Version(0, 0, 0, null);

    public static boolean isEqual(final String v1, final String v2) {
        return isEqual(Version.of(v1), v2);
    }

    public static boolean isEqual(final Version v1, final String v2) {
        return v1.equals(Version.of(v2));
    }

    /**
     * Static helper for creating a new version based on the specified string.
     *
     * @param version   the version.
     * @return          a new {@link Version} instance.
     */
    public static Version of(String version) {

        if (version.startsWith("v")) {
            version = version.substring(1);
        }

        int qualifier = version.indexOf("-");

        final String[] versions = qualifier > 0 ?
            version.substring(0, qualifier).split("\\.") :
            version.split("\\.");
        try {
            final int majorVersion = Integer.parseInt(versions[0]);
            final int minorVersion = versions.length > 1 ? Integer.parseInt(versions[1]) : 0;
            final int incrementalVersion = versions.length > 2 ? Integer.parseInt(versions[2]) : 0;

            return new Version(
                majorVersion,
                minorVersion,
                incrementalVersion,
                qualifier > 0 ? version.substring(qualifier + 1) : null,
                version
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version, cannot parse '" + version + "'");
        }
    }

    /**
     * Static helper method for returning the most recent stable version for a current {@link Version}.
     *
     * @param from     the current version.
     * @param versions the list of version.
     *
     * @return the last stable version.
     */
    public static Version getStable(final Version from, final Collection<Version> versions) {
        List<Version> compatibleVersions = versions.stream()
            .filter(v -> v.majorVersion() == from.majorVersion() && v.minorVersion() == from.minorVersion())
            .toList();
        if (compatibleVersions.isEmpty()) return null;
        return Version.getLatest(compatibleVersions);
    }

    /**
     * Static helper method for returning the latest version from a list of {@link Version}.
     *
     * @param versions  the list of version.
     * @return          the latest version.
     */
    public static Version getLatest(final Version...versions) {
        return getLatest(Stream.of(versions).toList());
    }

    /**
     * Static helper method for returning the latest version from a list of {@link Version}.
     *
     * @param versions  the list of version.
     * @return          the latest version.
     */
    public static Version getLatest(final Collection<Version> versions) {
        return versions.stream()
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalArgumentException("empty list"));
    }

    /**
     * Static helper for returning the latest version from a list of {@link Version}.
     *
     * @param versions  the list of version.
     * @return          the latest version.
     */
    public static Version getOldest(final Version...versions) {
        return getOldest(Stream.of(versions).toList());
    }

    /**
     * Static helper for returning the latest version from a list of {@link Version}.
     *
     * @param versions  the list of version.
     * @return          the latest version.
     */
    public static Version getOldest(final Collection<Version> versions) {
        return versions.stream()
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalArgumentException("empty list"));
    }

    private final int majorVersion;
    private final int minorVersion;
    private final int incrementalVersion;
    private final Qualifier qualifier;

    private final String originalVersion;

    /**
     * Creates a new {@link Version} instance.
     *
     * @param majorVersion          the major version (must be superior or equal to 0).
     * @param minorVersion          the minor version (must be superior or equal to 0).
     * @param incrementalVersion    the incremental version (must be superior or equal to 0).
     * @param qualifier             the qualifier.
     */
    public Version(final int majorVersion,
                   final int minorVersion,
                   final int incrementalVersion,
                   final String qualifier) {
        this(majorVersion, minorVersion, incrementalVersion, qualifier, null);
    }

    /**
     * Creates a new {@link Version} instance.
     *
     * @param majorVersion          the major version (must be superior or equal to 0).
     * @param minorVersion          the minor version (must be superior or equal to 0).
     * @param incrementalVersion    the incremental version (must be superior or equal to 0).
     * @param qualifier             the qualifier.
     * @param originalVersion       the original string version.
     */
    private Version(final int majorVersion,
                    final int minorVersion,
                    final int incrementalVersion,
                    final String qualifier,
                    final String originalVersion) {
        this.majorVersion =  requirePositive(majorVersion, "major");
        this.minorVersion = requirePositive(minorVersion, "minor");
        this.incrementalVersion = requirePositive(incrementalVersion, "incremental");
        this.qualifier = qualifier != null ? new Qualifier(qualifier) : null;
        this.originalVersion = originalVersion;
    }


    private static int requirePositive(int version, final String message) {
        if (version < 0) {
            throw new IllegalArgumentException(String.format("The '%s' version must super or equal to 0", message));
        }
        return version;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion;
    }

    public int incrementalVersion() {
        return incrementalVersion;
    }

    public Qualifier qualifier() {
        return qualifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Version)) return false;
        Version version = (Version) o;
        return majorVersion == version.majorVersion &&
            minorVersion == version.minorVersion &&
            incrementalVersion == version.incrementalVersion &&
            Objects.equals(qualifier, version.qualifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(majorVersion, minorVersion, incrementalVersion, qualifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (originalVersion != null) return originalVersion;

        String version =  majorVersion + "." + minorVersion + "." + incrementalVersion;
        return (qualifier != null) ? version +"-" + qualifier : version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final Version that) {

        int compareMajor = Integer.compare(that.majorVersion, this.majorVersion);
        if (compareMajor != 0) {
            return compareMajor;
        }

        int compareMinor = Integer.compare(that.minorVersion, this.minorVersion);
        if (compareMinor != 0) {
            return compareMinor;
        }

        int compareIncremental = Integer.compare(that.incrementalVersion, this.incrementalVersion);
        if (compareIncremental != 0) {
            return compareIncremental;
        }

        if (that.qualifier == null && this.qualifier == null) {
            return 0;
        } else if (that.qualifier == null) {
            return 1;
        } else if (this.qualifier == null) {
            return -1;
        }

        return this.qualifier.compareTo(that.qualifier);
    }

    /**
     * Checks whether this version is before the given one.
     *
     * @param version The version to compare.
     * @return {@code true} if this version is before.Otherwise {@code false}.
     */
    public boolean isBefore(final Version version) {
        return this.compareTo(version) > 0;
    }

    public static final class Qualifier implements Comparable<Qualifier> {

        private static final List<String> DEFAULT_QUALIFIER_NAME;

        static {
            // order is important
            DEFAULT_QUALIFIER_NAME = new ArrayList<>();
            DEFAULT_QUALIFIER_NAME.add("ALPHA");
            DEFAULT_QUALIFIER_NAME.add("BETA");
            DEFAULT_QUALIFIER_NAME.add("SNAPSHOT");
            DEFAULT_QUALIFIER_NAME.add("RC");
            DEFAULT_QUALIFIER_NAME.add("RELEASE");
        }

        private final String qualifier;
        private final String label;
        private final int priority;
        private final int number;

        /**
         * Creates a new {@link Qualifier} instance.
         * @param qualifier the qualifier string.
         */
        Qualifier(final String qualifier) {
            Objects.requireNonNull(qualifier, "qualifier cannot be null");
            this.qualifier = qualifier;
            this.label = getUniformQualifier(qualifier);
            this.priority = DEFAULT_QUALIFIER_NAME.indexOf(label);
            this.number = (label.length() < qualifier.length())  ? getQualifierNumber(qualifier) : 0;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object that) {
            if (this == that) return true;
            if (!(that instanceof Qualifier)) return false;
            return qualifier.equalsIgnoreCase(((Qualifier) that).qualifier);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(qualifier);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(final Qualifier that) {
            int compare = Integer.compare(that.priority, this.priority);
            return (compare != 0) ? compare : Integer.compare(that.number, this.number);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return qualifier;
        }
    }

    private static int getQualifierNumber(final String qualifier) {
        StringBuilder label = new StringBuilder();
        char[] chars = qualifier.toCharArray();
        for (char c : chars) {
            if (Character.isDigit(c)) {
                label.append(c);
            }
        }
        return label.isEmpty() ? 0 : Integer.parseInt(label.toString());
    }

    private static String getUniformQualifier(final String qualifier) {
        StringBuilder label = new StringBuilder();
        char[] chars = qualifier.toCharArray();
        for (char c : chars) {
            if (Character.isLetter(c)) {
                label.append(c);
            } else {
                break;
            }
        }
        return label.toString().toUpperCase();
    }
}