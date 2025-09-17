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
    public static Version of(final Object version) {

        if (Objects.isNull(version)) {
            throw new IllegalArgumentException("Invalid version, cannot parse null version");
        }
        
        String strVersion = version.toString();
        
        if (strVersion.startsWith("v")) {
            strVersion = strVersion.substring(1);
        }

        int qualifier = strVersion.indexOf("-");

        final String[] versions = qualifier > 0 ?
            strVersion.substring(0, qualifier).split("\\.") :
            strVersion.split("\\.");
        try {
            final int majorVersion = Integer.parseInt(versions[0]);
            final Integer minorVersion = versions.length > 1 ? Integer.parseInt(versions[1]) : null;
            final Integer incrementalVersion = versions.length > 2 ? Integer.parseInt(versions[2]) : null;

            return new Version(
                majorVersion,
                minorVersion,
                incrementalVersion,
                qualifier > 0 ? strVersion.substring(qualifier + 1) : null,
                strVersion
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version, cannot parse '" + version + "'");
        }
    }
    
    /**
     * Resolves the most appropriate stable version from a collection, based on a given input version.
     * <p>
     * The matching rules are:
     * <ul>
     *   <li>If {@code from} specifies only a major version (e.g. {@code 1}), return the latest stable version
     *       with the same major (e.g. {@code 1.2.3}).</li>
     *   <li>If {@code from} specifies a major and minor version only (e.g. {@code 1.2}), return the latest
     *       stable version with the same major and minor (e.g. {@code 1.2.3}).</li>
     *   <li>If {@code from} specifies a full version with major, minor, and patch (e.g. {@code 1.2.2}),
     *       then only return it if it is exactly present (and stable) in {@code versions}.
     *       No "upgrade" is performed in this case.</li>
     *   <li>If no suitable version is found, returns {@code null}.</li>
     * </ul>
     *
     * @param from     the reference version (may specify only major, or major+minor, or major+minor+patch).
     * @param versions the collection of candidate versions to resolve against.
     * @return the best matching stable version, or {@code null} if none match.
     */
    public static Version getStable(final Version from, final Collection<Version> versions) {
        // Case 1: "from" is only a major (e.g. 1)
        if (from.hasOnlyMajor()) {
            List<Version> sameMajor = versions.stream()
                .filter(v -> v.majorVersion() == from.majorVersion())
                .toList();
            return sameMajor.isEmpty() ? null : Version.getLatest(sameMajor);
        }
        
        // Case 2: "from" is major+minor only (e.g. 1.2)
        if (from.hasMajorAndMinorOnly()) {
            List<Version> sameMinor = versions.stream()
                .filter(v -> v.majorVersion() == from.majorVersion()
                    && v.minorVersion() == from.minorVersion())
                .toList();
            return sameMinor.isEmpty() ? null : Version.getLatest(sameMinor);
        }
        
        // Case 3: "from" is full version (major+minor+patch)
        if (versions.contains(from)) {
            return from;
        }
        
        // No match
        return null;
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
    private final Integer minorVersion;
    private final Integer patchVersion;
    private final Qualifier qualifier;

    private final String originalVersion;

    /**
     * Creates a new {@link Version} instance.
     *
     * @param majorVersion          the major version (must be superior or equal to 0).
     * @param minorVersion          the minor version (must be superior or equal to 0).
     * @param patchVersion    the incremental version (must be superior or equal to 0).
     * @param qualifier             the qualifier.
     */
    public Version(final int majorVersion,
                   final int minorVersion,
                   final int patchVersion,
                   final String qualifier) {
        this(majorVersion, minorVersion, patchVersion, qualifier, null);
    }

    /**
     * Creates a new {@link Version} instance.
     *
     * @param majorVersion          the major version (must be superior or equal to 0).
     * @param minorVersion          the minor version (must be superior or equal to 0).
     * @param patchVersion    the incremental version (must be superior or equal to 0).
     * @param qualifier             the qualifier.
     * @param originalVersion       the original string version.
     */
    private Version(final Integer majorVersion,
                    final Integer minorVersion,
                    final Integer patchVersion,
                    final String qualifier,
                    final String originalVersion) {
        this.majorVersion =  requirePositive(majorVersion, "major");
        this.minorVersion = requirePositive(minorVersion, "minor");
        this.patchVersion = requirePositive(patchVersion, "incremental");
        this.qualifier = qualifier != null ? new Qualifier(qualifier) : null;
        this.originalVersion = originalVersion;
    }


    private static Integer requirePositive(Integer version, final String message) {
        if (version != null && version < 0) {
            throw new IllegalArgumentException(String.format("The '%s' version must super or equal to 0", message));
        }
        return version;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion != null ? minorVersion : 0;
    }

    public int patchVersion() {
        return patchVersion != null ? patchVersion : 0;
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
        return Objects.equals(majorVersion,version.majorVersion) &&
            Objects.equals(minorVersion, version.minorVersion) &&
            Objects.equals(patchVersion,version.patchVersion) &&
            Objects.equals(qualifier, version.qualifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(majorVersion, minorVersion, patchVersion, qualifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (originalVersion != null) return originalVersion;

        String version =  majorVersion + "." + minorVersion + "." + patchVersion;
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

        int compareIncremental = Integer.compare(that.patchVersion, this.patchVersion);
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
     * @return true if only major is specified (e.g. "1")
     */
    private boolean hasOnlyMajor() {
        return minorVersion == null && patchVersion == null;
    }
    
    /**
     * @return true if major+minor are specified, but no patch (e.g. "1.2")
     */
    private boolean hasMajorAndMinorOnly() {
        return minorVersion != null && patchVersion == null;
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